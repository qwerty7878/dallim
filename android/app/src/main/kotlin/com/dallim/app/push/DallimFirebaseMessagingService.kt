package com.dallim.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dallim.app.MainActivity
import com.dallim.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 폰 시스템 푸시 수신 (docs/01-feature-spec.md §1.7 2단계, docs/02-api-spec.md 10장).
 *
 * - [onNewToken]: 토큰이 새로 발급되거나 갱신될 때마다 서버에 등록한다.
 * - [onMessageReceived]: 서버가 9.4(알림 생성 트리거) 시점에 함께 발송하는 푸시를 받아 시스템
 *   알림으로 표시한다. title/body는 인앱 알림(9.1)과 동일한 포맷 — 별도 딥링크/탭 동작은
 *   SPEC(10.3)에서 유보했으므로 탭하면 앱만 연다(CLAUDE.md 규칙 1 — SPEC에 없는 기능 확장 금지).
 */
@AndroidEntryPoint
class DallimFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var deviceTokenRegistrar: DeviceTokenRegistrar

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch { deviceTokenRegistrar.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: return
        val body = message.notification?.body.orEmpty()
        showSystemNotification(title, body)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showSystemNotification(title: String, body: String) {
        createNotificationChannelIfNeeded()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // 사용자가 알림 권한을 거부한 상태 — 표시만 건너뛴다(인앱 알림함 레코드는 서버에 그대로
            // 남아 S-46에서 확인 가능하므로 여기서 별도 처리할 필요 없음).
            return
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification_run)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // 알림마다 다른 ID를 써서 여러 건이 와도 서로 덮어쓰지 않게 한다(예: 완주 알림 여러 건).
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "달림 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "완주 등 달림 알림 (docs/02-api-spec.md 9장/10장)"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "dallim_push"
    }
}
