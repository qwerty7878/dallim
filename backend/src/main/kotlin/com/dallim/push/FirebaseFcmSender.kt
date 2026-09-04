package com.dallim.push

import com.dallim.plugins.DallimConfig
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

/**
 * Real Firebase Admin SDK wiring (docs/02-api-spec.md 10.2). The service account key path comes
 * from [DallimConfig.FcmSettings.credentialsPath] (dallim.fcm.credentialsPath /
 * FCM_CREDENTIALS_PATH env var) -- NEVER hardcoded here, per CLAUDE.md / the task's absolute
 * rule; the key file itself is gitignored (backend/secrets/, see backend/.gitignore).
 *
 * If the file is missing or unreadable (e.g. CI, a fresh checkout without the secret dropped in
 * yet, the test profile which deliberately points at a nonexistent path -- see
 * application-test.conf), FCM is disabled: [send] becomes a no-op that returns [FcmSendResult
 * .OtherFailure] instead of throwing, so the rest of the app (including in-app notification
 * creation) keeps working normally.
 */
class FirebaseFcmSender(config: DallimConfig) : FcmSender {
    private val logger = LoggerFactory.getLogger(FirebaseFcmSender::class.java)
    private val messaging: FirebaseMessaging? = initMessaging(config.fcm.credentialsPath)

    private fun initMessaging(credentialsPath: String): FirebaseMessaging? {
        val file = File(credentialsPath)
        if (!file.exists()) {
            logger.warn(
                "FCM push disabled: credentials file not found at '{}' (dallim.fcm.credentialsPath / " +
                    "FCM_CREDENTIALS_PATH). In-app notifications still work; device push will be skipped.",
                credentialsPath,
            )
            return null
        }

        return try {
            val credentials = FileInputStream(file).use { GoogleCredentials.fromStream(it) }
            val options = FirebaseOptions.builder().setCredentials(credentials).build()
            val app = FirebaseApp.getApps().firstOrNull() ?: FirebaseApp.initializeApp(options)
            FirebaseMessaging.getInstance(app)
        } catch (e: Exception) {
            logger.warn("FCM push disabled: failed to initialize Firebase Admin SDK from '{}'", credentialsPath, e)
            null
        }
    }

    override fun send(token: String, title: String, body: String): FcmSendResult {
        val client = messaging ?: return FcmSendResult.OtherFailure

        val message = Message.builder()
            .setToken(token)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .build()

        return try {
            client.send(message)
            FcmSendResult.Success
        } catch (e: FirebaseMessagingException) {
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED) {
                FcmSendResult.TokenInvalid
            } else {
                logger.warn("FCM send failed (errorCode={}): {}", e.messagingErrorCode, e.message)
                FcmSendResult.OtherFailure
            }
        } catch (e: Exception) {
            logger.warn("FCM send failed unexpectedly", e)
            FcmSendResult.OtherFailure
        }
    }
}
