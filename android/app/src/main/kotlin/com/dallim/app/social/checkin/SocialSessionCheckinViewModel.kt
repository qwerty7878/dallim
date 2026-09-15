package com.dallim.app.social.checkin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.app.onboarding.firstroute.CurrentLocationProvider
import com.dallim.network.social.SocialSessionApi
import com.dallim.network.social.SocialSessionCheckinRequest
import com.dallim.network.social.SocialSessionReadyCheckItem
import com.dallim.network.user.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SocialSessionCheckinUiState {
    data object Loading : SocialSessionCheckinUiState
    data class Error(val message: String) : SocialSessionCheckinUiState

    /**
     * @param myUserId `GET /users/me`로 얻은 내 id — [items] 중 나를 찾아 체크인 버튼/상태를
     *   판단한다(나이/성별 없이 그냥 id 비교, CLAUDE.md 규칙 2와 무관).
     * @param isHost 내가 이 세션의 호스트인지 — `items`에서 내 id의 `isHost` 필드로 판단한다.
     * @param started `POST .../start` 이후 true. true가 되면 "평가하러 가기" CTA를 보여준다
     *   (S-38 진입점, 작업 브리핑 지시: 별도 "세션 종료" 액션이 없어 이 값으로 근사).
     */
    data class Success(
        val myUserId: String,
        val isHost: Boolean,
        val items: List<SocialSessionReadyCheckItem>,
        val started: Boolean,
        val isCheckingIn: Boolean = false,
        val checkinToastMessage: String? = null,
        val isStarting: Boolean = false,
        val manualConfirmInProgressUserId: String? = null,
    ) : SocialSessionCheckinUiState {
        val myStatus: SocialSessionReadyCheckItem? get() = items.firstOrNull { it.userId == myUserId }
    }
}

/**
 * S-36 GPS 체크인 + S-37 Ready Check (docs/02-api-spec.md 17.14/17.15) — 작업 브리핑 지시대로
 * 별도 화면 두 개가 아니라 "체크인하기" 액션과 전원의 체크인 현황판을 한 화면에 잇는다. 5초
 * 주기로 [ready-check]를 다시 불러와 다른 참가자들의 체크인을 준실시간으로 반영한다(WebSocket을
 * 새로 놓을 만큼 무거운 데이터가 아니라 폴링으로 충분 — 과설계 금지).
 */
@HiltViewModel
class SocialSessionCheckinViewModel @Inject constructor(
    private val socialSessionApi: SocialSessionApi,
    private val userApi: UserApi,
    private val locationProvider: CurrentLocationProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** [SocialSessionCheckinRoute]가 S-38(평가) 진입 시 그대로 넘겨준다. */
    val sessionId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_SOCIAL_SESSION_ID]) { "sessionId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<SocialSessionCheckinUiState>(SocialSessionCheckinUiState.Loading)
    val uiState: StateFlow<SocialSessionCheckinUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SocialSessionCheckinUiState.Loading
            val meResult = safeApiCall { userApi.getMe() }
            val myUserId = (meResult as? UiResult.Success)?.data?.userId
            if (myUserId == null) {
                _uiState.value = SocialSessionCheckinUiState.Error((meResult as? UiResult.Error)?.message ?: "내 정보를 불러오지 못했어요.")
                return@launch
            }
            when (val result = safeApiCall { socialSessionApi.getReadyCheck(sessionId) }) {
                is UiResult.Success -> {
                    val myIsHost = result.data.items.firstOrNull { it.userId == myUserId }?.isHost ?: false
                    _uiState.value = SocialSessionCheckinUiState.Success(
                        myUserId = myUserId,
                        isHost = myIsHost,
                        items = result.data.items,
                        started = result.data.started,
                    )
                    startPolling()
                }
                is UiResult.Error -> _uiState.value = SocialSessionCheckinUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                refreshSilently()
            }
        }
    }

    private suspend fun refreshSilently() {
        if (_uiState.value !is SocialSessionCheckinUiState.Success) return
        when (val result = safeApiCall { socialSessionApi.getReadyCheck(sessionId) }) {
            is UiResult.Success -> updateSuccess {
                it.copy(items = result.data.items, started = result.data.started)
            }
            else -> Unit // 폴링 실패는 조용히 무시 — 다음 주기에 다시 시도.
        }
    }

    /** [com.dallim.app.social.checkin.SocialSessionCheckinRoute]가 위치 권한을 이미 확인한
     * 뒤에만 호출한다(S-20과 동일 관례) — 여기서는 단발성 위치 획득만 한다(CLAUDE.md 규칙 4,
     * 스트리밍 아님). */
    fun onCheckinClick() {
        val state = _uiState.value as? SocialSessionCheckinUiState.Success ?: return
        if (state.isCheckingIn) return
        viewModelScope.launch {
            updateSuccess { it.copy(isCheckingIn = true) }
            val location = locationProvider.getCurrentLocation()
            if (location == null) {
                updateSuccess {
                    it.copy(isCheckingIn = false, checkinToastMessage = "위치를 확인할 수 없어요. GPS 상태를 확인해주세요.")
                }
                return@launch
            }
            val (lat, lng) = location
            val request = SocialSessionCheckinRequest(lat = lat, lng = lng)
            when (val result = safeApiCall { socialSessionApi.checkinSocialSession(sessionId, request) }) {
                is UiResult.Success -> {
                    val distance = "%.0f".format(result.data.distanceToMeetingPointM)
                    updateSuccess { it.copy(isCheckingIn = false, checkinToastMessage = "체크인 완료! 집결지까지 ${distance}m") }
                    refreshSilently()
                }
                is UiResult.Error -> updateSuccess {
                    it.copy(isCheckingIn = false, checkinToastMessage = mapCheckinErrorMessage(result.code, result.message))
                }
                UiResult.Loading -> Unit
            }
        }
    }

    private fun mapCheckinErrorMessage(code: String?, fallback: String): String = when (code) {
        "SESSION_CHECKIN_OUTSIDE_WINDOW" -> "체크인 가능한 시간이 아니에요 (집결 30분 전 ~ 15분 후)."
        "SESSION_CHECKIN_TOO_FAR" -> "집결지에서 너무 멀어요. 150m 이내에서 다시 시도해주세요."
        "SESSION_ALREADY_STARTED" -> "이미 러닝이 시작됐어요."
        else -> fallback
    }

    /** 호스트 전용 "달리기 시작" — 미체크인자가 있어도 항상 성공하고, 그들은 서버가 일괄
     * NO_SHOW로 전환한다(17.15). */
    fun onStartClick() {
        val state = _uiState.value as? SocialSessionCheckinUiState.Success ?: return
        if (!state.isHost || state.isStarting) return
        viewModelScope.launch {
            updateSuccess { it.copy(isStarting = true) }
            when (val result = safeApiCall { socialSessionApi.startSocialSession(sessionId) }) {
                is UiResult.Success -> {
                    updateSuccess { it.copy(isStarting = false, checkinToastMessage = "달리기를 시작했어요!") }
                    refreshSilently()
                }
                is UiResult.Error -> updateSuccess { it.copy(isStarting = false, checkinToastMessage = result.message) }
                UiResult.Loading -> Unit
            }
        }
    }

    /** 호스트 전용 수동 확인 — GPS 오차/실내 집결/NO_SHOW 이의제기 대응(17.15). */
    fun onManualConfirmClick(userId: String) {
        val state = _uiState.value as? SocialSessionCheckinUiState.Success ?: return
        if (!state.isHost || state.manualConfirmInProgressUserId != null) return
        viewModelScope.launch {
            updateSuccess { it.copy(manualConfirmInProgressUserId = userId) }
            when (val result = safeApiCall { socialSessionApi.manualConfirmCheckin(sessionId, userId) }) {
                is UiResult.Success -> {
                    updateSuccess { it.copy(manualConfirmInProgressUserId = null) }
                    refreshSilently()
                }
                is UiResult.Error -> updateSuccess {
                    it.copy(manualConfirmInProgressUserId = null, checkinToastMessage = result.message)
                }
                UiResult.Loading -> Unit
            }
        }
    }

    fun onToastMessageShown() = updateSuccess { it.copy(checkinToastMessage = null) }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    private inline fun updateSuccess(transform: (SocialSessionCheckinUiState.Success) -> SocialSessionCheckinUiState.Success) {
        _uiState.update { state -> if (state is SocialSessionCheckinUiState.Success) transform(state) else state }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5000L
    }
}
