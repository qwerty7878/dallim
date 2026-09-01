package com.dallim.network.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TokenAuthenticator]는 OkHttp의 백그라운드 디스패처 스레드에서 동기(runBlocking)로 돌기
 * 때문에, 리프레시 토큰까지 만료/무효라 `clearTokens()`를 부른 순간 그 사실을 Compose
 * 쪽(메인 스레드의 NavHost)으로 알려줄 방법이 따로 필요하다 — 이 버스가 그 통로다.
 *
 * `notifySessionExpired()`는 suspend가 아닌 [tryEmit]으로 발행한다: OkHttp Authenticator는
 * suspend 함수가 아니라 emit()을 쓸 수 없다. `extraBufferCapacity = 1`이라 NavHost가 아직
 * collect를 시작하기 전에(예: 콜드 스타트 타이밍) 이벤트가 발행돼도 버퍼에 1건 남아 있다가
 * 구독 즉시 전달된다.
 */
@Singleton
class SessionEventBus @Inject constructor() {

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    fun notifySessionExpired() {
        _sessionExpired.tryEmit(Unit)
    }
}
