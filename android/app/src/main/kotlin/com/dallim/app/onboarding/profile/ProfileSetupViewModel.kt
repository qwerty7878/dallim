package com.dallim.app.onboarding.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.network.user.ProfileRequest
import com.dallim.network.user.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NicknameCheckState { IDLE, CHECKING, AVAILABLE, TAKEN, ERROR }

data class ProfileSetupUiState(
    val nickname: String = "",
    val nicknameCheckState: NicknameCheckState = NicknameCheckState.IDLE,
    val selectedAvatarId: String? = null,
    val runningExperience: RunningExperience? = null,
    val comfortablePace: ComfortablePace? = null,
    val gender: Gender? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
) {
    val canSubmit: Boolean
        get() = nicknameCheckState == NicknameCheckState.AVAILABLE &&
            selectedAvatarId != null &&
            runningExperience != null &&
            comfortablePace != null &&
            gender != null &&
            !isSubmitting
}

sealed interface ProfileSetupNavigationEvent {
    data object GoToPermission : ProfileSetupNavigationEvent

    /**
     * `runningExperience == OVER_1_YEAR`일 때만 S-04b(러닝 커리어 입력)로 보낸다 —
     * docs/달림_화면별_상세기획서_v1.3.md PART 3-A "S-04b" 노출 조건. [ComfortablePace.apiValue]를
     * 함께 실어 보내 S-04b가 페이스 제안과 비교할 수 있게 한다.
     */
    data class GoToCareerEntry(val comfortablePaceApiValue: String) : ProfileSetupNavigationEvent
}

/**
 * S-04 프로필 설정. 닉네임은 300ms 디바운스 후 `GET /users/nickname-check` 호출
 * (docs/01-feature-spec.md 1.1 "핵심 비기능 요구사항"). 제출은 `POST /users/me/profile` —
 * gender는 요청에만 실려 나가고 이 ViewModel의 State에도 화면 재노출용으로 별도 보관하지
 * 않는다(선택값을 그대로 들고 있다가 전송만 함, CLAUDE.md 규칙 2).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val userApi: UserApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<ProfileSetupNavigationEvent>()
    val navigationEvents: SharedFlow<ProfileSetupNavigationEvent> = _navigationEvents.asSharedFlow()

    private val nicknameInput = MutableStateFlow("")

    init {
        viewModelScope.launch {
            nicknameInput
                .debounce(300)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collect { value -> checkNickname(value) }
        }
    }

    fun onNicknameChange(value: String) {
        _uiState.value = _uiState.value.copy(
            nickname = value,
            nicknameCheckState = if (value.isBlank()) NicknameCheckState.IDLE else NicknameCheckState.CHECKING,
        )
        nicknameInput.value = value
    }

    private suspend fun checkNickname(value: String) {
        // Stale-response guard: if the field changed again while this call was in flight, drop it.
        if (_uiState.value.nickname != value) return
        runCatching { userApi.checkNickname(value) }
            .onSuccess { response ->
                if (_uiState.value.nickname != value) return@onSuccess
                val body = response.body()
                val data = body?.data
                _uiState.value = _uiState.value.copy(
                    nicknameCheckState = when {
                        !response.isSuccessful || body?.success != true || data == null -> NicknameCheckState.ERROR
                        data.available -> NicknameCheckState.AVAILABLE
                        else -> NicknameCheckState.TAKEN
                    },
                )
            }
            .onFailure {
                if (_uiState.value.nickname == value) {
                    _uiState.value = _uiState.value.copy(nicknameCheckState = NicknameCheckState.ERROR)
                }
            }
    }

    fun onAvatarSelected(avatarId: String) {
        _uiState.value = _uiState.value.copy(selectedAvatarId = avatarId)
    }

    fun onRunningExperienceSelected(value: RunningExperience) {
        _uiState.value = _uiState.value.copy(runningExperience = value)
    }

    fun onComfortablePaceSelected(value: ComfortablePace) {
        _uiState.value = _uiState.value.copy(comfortablePace = value)
    }

    fun onGenderSelected(value: Gender) {
        _uiState.value = _uiState.value.copy(gender = value)
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val avatarId = state.selectedAvatarId ?: return
        val experience = state.runningExperience ?: return
        val pace = state.comfortablePace ?: return
        val gender = state.gender ?: return

        viewModelScope.launch {
            _uiState.value = state.copy(isSubmitting = true, submitError = null)
            runCatching {
                userApi.submitProfile(
                    ProfileRequest(
                        nickname = state.nickname,
                        avatarId = avatarId,
                        runningExperience = experience.apiValue,
                        comfortablePace = pace.apiValue,
                        gender = gender.apiValue,
                    ),
                )
            }.onSuccess { response ->
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                    _navigationEvents.emit(
                        if (experience == RunningExperience.OVER_1_YEAR) {
                            ProfileSetupNavigationEvent.GoToCareerEntry(pace.apiValue)
                        } else {
                            ProfileSetupNavigationEvent.GoToPermission
                        },
                    )
                } else {
                    val message = if (response.code() == 409) "이미 사용 중인 닉네임이에요." else "프로필 저장에 실패했어요."
                    _uiState.value = _uiState.value.copy(isSubmitting = false, submitError = message)
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSubmitting = false, submitError = it.message ?: "네트워크 오류가 발생했어요.")
            }
        }
    }
}
