package com.dallim.app.push

import android.util.Log
import com.dallim.network.user.DeviceTokenRequest
import com.dallim.network.user.UserApi
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FCM 토큰을 서버에 등록하는 단일 진입점 (docs/02-api-spec.md 10.1, docs/01-feature-spec.md §1.7
 * 2단계). 호출 지점은 셋:
 * - [DallimFirebaseMessagingService.onNewToken] — 토큰이 새로 발급/갱신될 때.
 * - `SplashViewModel` — 이미 로그인된 상태로 앱을 시작할 때("앱 시작 시").
 * - `LoginViewModel`/`EmailAuthViewModel` — 로그인/회원가입 성공 직후("로그인 성공 직후").
 *
 * `onNewToken`은 최초 발급 이후에는 토큰이 실제로 바뀔 때만 불리므로, 이미 토큰을 갖고 있던
 * 기기가 로그인만 새로 하는 경우를 놓치지 않으려면 로그인 성공 지점에서도 현재 토큰을 다시
 * 읽어 등록해야 한다(요구사항 3).
 *
 * `POST /users/me/device-tokens`는 upsert라 중복 호출해도 안전하고(10.1 "중복 저장하지 않는다"),
 * 인증 없이 호출되면 그냥 401로 실패하므로 실패는 조용히 무시한다 — 푸시 등록 실패가 로그인/앱
 * 실행 자체를 막을 이유는 없다.
 */
@Singleton
class DeviceTokenRegistrar @Inject constructor(
    private val userApi: UserApi,
) {

    /** 로그인 성공 직후 / 앱 시작 시 — 지금 갖고 있는(또는 새로 발급받는) 토큰을 다시 등록한다. */
    suspend fun registerCurrentToken() {
        runCatching { FirebaseMessaging.getInstance().token.await() }
            .onSuccess { token -> registerToken(token) }
            .onFailure { Log.w(TAG, "FCM 토큰을 가져오지 못했어요.", it) }
    }

    /** [DallimFirebaseMessagingService.onNewToken]에서 이미 토큰 문자열을 받은 경우. */
    suspend fun registerToken(token: String) {
        runCatching { userApi.registerDeviceToken(DeviceTokenRequest(fcmToken = token)) }
            .onFailure { Log.w(TAG, "기기 토큰 등록에 실패했어요.", it) }
    }

    private companion object {
        const val TAG = "DeviceTokenRegistrar"
    }
}
