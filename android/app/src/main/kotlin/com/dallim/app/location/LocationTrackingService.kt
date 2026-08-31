package com.dallim.app.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dallim.app.MainActivity
import com.dallim.app.R
import com.dallim.ui.components.GeoPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import javax.inject.Inject

/**
 * GPS 트래킹 파이프라인의 두 번째 단계 (docs/01-feature-spec.md §1.3):
 *
 * ```
 * FusedLocationProviderClient (1초 간격 요청)
 *     -> ForegroundService (이 클래스, 화면 OFF/백그라운드에서도 유지)
 *     -> Room DB (실제 기록은 직전 지점에서 5~10m 이상 이동했을 때만)
 * ```
 *
 * S-22(일시정지/재개/종료)의 상태 머신도 여기서 관리한다. 배치 업로드(WorkManager)와 서버
 * 완주판정 호출은 이 서비스의 책임이 아니다 — Finish 시점엔 위치 수집만 멈추고 서비스는 바로
 * 종료하며, 이후 업로드/판정은 화면(ViewModel)이 [RunTrackingRepository]의 FINISHING phase를
 * 보고 이어받는다(서비스 생명주기와 네트워크 재시도 로직을 분리하기 위함).
 */
@AndroidEntryPoint
class LocationTrackingService : LifecycleService() {

    @Inject lateinit var repository: RunTrackingRepository

    @Inject lateinit var gpsPointDao: GpsPointDao

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private var plannedRoute: List<GeoPoint> = emptyList()
    private var runId: String? = null

    private var lastRecordedPoint: GeoPoint? = null
    private var runStartElapsedRealtime: Long = 0L
    private var pausedAccumulatedMillis: Long = 0L
    private var pauseStartedAtElapsedRealtime: Long? = null

    private val deviationTracker = RouteDeviationTracker()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onNewLocation(location.latitude, location.longitude, location.accuracy)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_FINISH -> handleFinish()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission") // S-20에서 포그라운드+백그라운드 위치 권한을 이미 확인/요청함
    private fun handleStart(intent: Intent) {
        val startRunId = intent.getStringExtra(EXTRA_RUN_ID) ?: return
        val routeJson = intent.getStringExtra(EXTRA_PLANNED_ROUTE_JSON)
        runId = startRunId
        plannedRoute = decodeRoute(routeJson)
        lastRecordedPoint = null
        pausedAccumulatedMillis = 0L
        pauseStartedAtElapsedRealtime = null
        runStartElapsedRealtime = SystemClock.elapsedRealtime()
        deviationTracker.reset()

        repository.update {
            RunTrackingSnapshot(phase = RunPhase.RUNNING, runId = startRunId)
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(distanceMeters = 0.0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        requestLocationUpdates()
    }

    private fun handlePause() {
        if (repository.state.value.phase != RunPhase.RUNNING) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        pauseStartedAtElapsedRealtime = SystemClock.elapsedRealtime()
        repository.update { it.copy(phase = RunPhase.PAUSED) }
    }

    @SuppressLint("MissingPermission")
    private fun handleResume() {
        if (repository.state.value.phase != RunPhase.PAUSED) return
        pauseStartedAtElapsedRealtime?.let { pausedAccumulatedMillis += SystemClock.elapsedRealtime() - it }
        pauseStartedAtElapsedRealtime = null
        repository.update { it.copy(phase = RunPhase.RUNNING) }
        requestLocationUpdates()
    }

    private fun handleFinish() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        repository.update { it.copy(phase = RunPhase.FINISHING) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun onNewLocation(lat: Double, lng: Double, accuracyM: Float) {
        val currentRunId = runId ?: return
        val point = GeoPoint(lng = lng, lat = lat)
        val nowMillis = System.currentTimeMillis()

        // S-23: 계획 경로 대비 이격거리는 기록 필터와 무관하게 매 위치 콜백마다 즉시 반응해야 한다.
        val distanceToRoute = RouteDeviationChecker.distanceToRouteMeters(point, plannedRoute)
        val isOffRoute = deviationTracker.onLocation(distanceToRoute, nowMillis)

        val previous = lastRecordedPoint
        val movedEnough = previous == null ||
            LocalPrecheckCalculator.totalDistanceMeters(listOf(previous, point)) >= MIN_RECORD_DISTANCE_M

        repository.update { snapshot ->
            var updated = snapshot.copy(
                currentLocation = point,
                gpsAccuracyM = accuracyM,
                isOffRoute = isOffRoute,
                elapsedMillis = elapsedMillisSinceStart(),
            )
            if (movedEnough) {
                val newPath = snapshot.actualPath + point
                val newDistance = LocalPrecheckCalculator.totalDistanceMeters(newPath)
                val elapsedSeconds = updated.elapsedMillis / 1000.0
                val paceSecPerKm = if (newDistance > 0 && elapsedSeconds > 0) {
                    (elapsedSeconds / (newDistance / 1000.0)).toInt()
                } else {
                    null
                }
                updated = updated.copy(
                    actualPath = newPath,
                    distanceMeters = newDistance,
                    currentPaceSecPerKm = paceSecPerKm,
                    coveragePercent = LocalPrecheckCalculator.coveragePercent(plannedRoute, newPath),
                )
            }
            updated
        }

        if (movedEnough) {
            lastRecordedPoint = point
            lifecycleScope.launch {
                gpsPointDao.insert(
                    GpsPointEntity(
                        runId = currentRunId,
                        lat = lat,
                        lng = lng,
                        timestampMillis = nowMillis,
                        accuracyM = accuracyM,
                    ),
                )
            }
            updateNotification()
        }
    }

    private fun elapsedMillisSinceStart(): Long {
        val pausedSoFar = pausedAccumulatedMillis +
            (pauseStartedAtElapsedRealtime?.let { SystemClock.elapsedRealtime() - it } ?: 0L)
        return (SystemClock.elapsedRealtime() - runStartElapsedRealtime - pausedSoFar).coerceAtLeast(0L)
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(repository.state.value.distanceMeters))
    }

    private fun buildNotification(distanceMeters: Double): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val distanceKm = distanceMeters / 1000.0
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("달림이 GPS를 기록하고 있어요")
            .setContentText("%.2fkm 달리는 중".format(distanceKm))
            .setSmallIcon(R.drawable.ic_notification_run)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "러닝 트래킹", NotificationManager.IMPORTANCE_LOW).apply {
            description = "러닝 중 GPS 기록 상태 알림"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    private fun decodeRoute(json: String?): List<GeoPoint> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.decodeFromString<List<List<Double>>>(json).mapNotNull {
                if (it.size >= 2) GeoPoint(lng = it[0], lat = it[1]) else null
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val ACTION_START = "com.dallim.app.location.action.START"
        const val ACTION_PAUSE = "com.dallim.app.location.action.PAUSE"
        const val ACTION_RESUME = "com.dallim.app.location.action.RESUME"
        const val ACTION_FINISH = "com.dallim.app.location.action.FINISH"

        const val EXTRA_RUN_ID = "extra_run_id"
        const val EXTRA_PLANNED_ROUTE_JSON = "extra_planned_route_json"

        private const val CHANNEL_ID = "dallim_running_tracking"
        private const val NOTIFICATION_ID = 4200

        /** 위치 요청 간격 — 1~3초 범위 중 1초 (docs/01-feature-spec.md §1.3, S-21 표). */
        private const val LOCATION_INTERVAL_MS = 1000L

        /** 실제 Room 기록 최소 이동거리 — 5~10m 범위 중간값 (docs §1.3, S-21 표). */
        private const val MIN_RECORD_DISTANCE_M = 8.0

        fun encodeRoute(route: List<GeoPoint>): String =
            Json.encodeToString(route.map { listOf(it.lng, it.lat) })

        fun startIntent(context: Context, runId: String, plannedRoute: List<GeoPoint>): Intent =
            Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RUN_ID, runId)
                putExtra(EXTRA_PLANNED_ROUTE_JSON, encodeRoute(plannedRoute))
            }

        fun pauseIntent(context: Context): Intent =
            Intent(context, LocationTrackingService::class.java).apply { action = ACTION_PAUSE }

        fun resumeIntent(context: Context): Intent =
            Intent(context, LocationTrackingService::class.java).apply { action = ACTION_RESUME }

        fun finishIntent(context: Context): Intent =
            Intent(context, LocationTrackingService::class.java).apply { action = ACTION_FINISH }
    }
}
