package com.dallim.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 걸음수 수집 — docs/달림_화면별_상세기획서_v1.3.md PART 4.1 "부정행위 방지" 근거
 * (2026-09-06 SPEC 편입). 향후 "걸음수 대비 이동거리 불일치"로 GPS 조작을 탐지하는 신호로
 * 쓰일 예정이지만, **이번 라운드는 순수 수집·전송만** 한다 — 완주 판정(로컬 프리체크 포함,
 * [LocalPrecheckCalculator])에는 전혀 관여하지 않는다(CLAUDE.md rule 3).
 *
 * [Sensor.TYPE_STEP_COUNTER]는 기기 재부팅 이후의 누적 걸음수를 돌려주는 센서다. 러닝 시작
 * 시점 값을 기준으로 잡아두고, 종료 시점 값과의 차이를 "이번 러닝의 걸음수"로 계산한다.
 *
 * 센서가 없는 기기(대부분의 에뮬레이터 포함), API 29+에서 `ACTIVITY_RECOGNITION` 권한이
 * 없는 경우, 혹은 러닝 도중 센서 이벤트를 한 번도 못 받은 경우 모두 조용히 null을 반환한다 —
 * 에러 처리나 사용자 노출은 하지 않는다. **이 권한을 얻기 위한 새 요청 UI/다이얼로그는 만들지
 * 않는다**(SPEC S-05/S-20에 없음, 이번 작업 지시) — 이미 허용돼 있을 때만 사용하고, 없으면
 * 그냥 확인만 하고 넘어간다.
 */
@Singleton
class StepCounterTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepCounterSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    @Volatile private var startCumulativeSteps: Float? = null
    @Volatile private var latestCumulativeSteps: Float? = null
    @Volatile private var isRegistered = false

    /** [LocationTrackingService]의 handleStart에서 호출 — 이번 러닝의 걸음수 기준점을 리셋한다. */
    fun start() {
        startCumulativeSteps = null
        latestCumulativeSteps = null
        if (!hasActivityRecognitionPermission()) {
            Log.d(TAG, "start: ACTIVITY_RECOGNITION 권한 없음 — 걸음수 수집 건너뜀(null 폴백)")
            return
        }
        val sensor = stepCounterSensor
        if (sensor == null) {
            Log.d(TAG, "start: TYPE_STEP_COUNTER 센서 없음 — 걸음수 null 폴백")
            return
        }
        isRegistered = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
    }

    /**
     * [RunFinishCoordinator.finish]에서 호출 — 시작~현재까지 누적된 걸음수 차이를 반환하고
     * 센서 리스너를 해제한다. 시작 이후 유효한 값을 한 번도 못 받았으면(센서 없음/권한 없음/
     * 이벤트 미수신) null.
     */
    fun stopAndConsume(): Int? {
        if (isRegistered) {
            sensorManager?.unregisterListener(this)
            isRegistered = false
        }
        val start = startCumulativeSteps
        val latest = latestCumulativeSteps
        val stepCount = if (start != null && latest != null) (latest - start).toInt().coerceAtLeast(0) else null
        Log.d(TAG, "stopAndConsume: stepCount=$stepCount")
        return stepCount
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val cumulative = event.values.firstOrNull() ?: return
        if (startCumulativeSteps == null) startCumulativeSteps = cumulative
        latestCumulativeSteps = cumulative
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun hasActivityRecognitionPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val TAG = "StepCounterTracker"
    }
}
