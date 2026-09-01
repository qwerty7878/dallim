package com.dallim.app.navigation

import androidx.lifecycle.ViewModel
import com.dallim.network.auth.SessionEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

/**
 * [DallimNavHost]가 [SessionEventBus](core-network, OkHttp 백그라운드 스레드에서 발행)를
 * 구독하기 위한 얇은 어댑터. `DallimNavHost`는 `@Composable`이라 Hilt 생성자 주입을 직접 받을
 * 수 없으므로, 다른 화면들(MyViewModel 등)과 동일하게 `hiltViewModel()` 관례로 이 ViewModel을
 * 얻어 버스를 꺼내온다.
 */
@HiltViewModel
class SessionEventViewModel @Inject constructor(
    sessionEventBus: SessionEventBus,
) : ViewModel() {
    val sessionExpired: SharedFlow<Unit> = sessionEventBus.sessionExpired
}
