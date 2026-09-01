package com.dallim.app.onboarding.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.network.auth.TokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** S-00 스플래시 목적지 — docs/01-feature-spec.md 1.1: 유효 -> S-10 / 무효 -> S-01. */
sealed interface SplashDestination {
    data object Loading : SplashDestination
    data object Home : SplashDestination
    data object OnboardingCarousel : SplashDestination
}

/**
 * Resolves the saved-token check described in docs/01-feature-spec.md 1.1 (S-00).
 *
 * NOTE(android-dev): "토큰 유효성 확인" here is a presence check (access/refresh token saved
 * locally), not a decoded-JWT expiry check or a server round-trip — there's no
 * `GET /users/me`-style "am I still logged in" verification call in docs/02-api-spec.md to hang
 * this off of, so a lightweight local check is what's in scope for S-00. If the saved access
 * token has actually expired, the very first authenticated call after landing on Home will 401
 * and TokenAuthenticator (core-network) will transparently refresh or, if the refresh token is
 * also dead, clear the stored tokens *and* publish to `SessionEventBus`
 * ([com.dallim.network.auth.SessionEventBus]) — `DallimNavHost` subscribes to that bus (via
 * `SessionEventViewModel`) and actually bounces the user back to S-02(로그인) from wherever they
 * are, popping the whole nav graph the same way `MyViewModel`'s logout does.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenProvider: TokenProvider,
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val destination: StateFlow<SplashDestination> = _destination

    init {
        viewModelScope.launch {
            val hasSavedSession = withContext(Dispatchers.IO) {
                !tokenProvider.getAccessToken().isNullOrBlank() && !tokenProvider.getRefreshToken().isNullOrBlank()
            }
            _destination.value = if (hasSavedSession) SplashDestination.Home else SplashDestination.OnboardingCarousel
        }
    }
}
