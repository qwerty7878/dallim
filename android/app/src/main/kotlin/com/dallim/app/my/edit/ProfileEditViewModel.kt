package com.dallim.app.my.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.network.user.PatchMeRequest
import com.dallim.network.user.UserApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileEditUiState {
    data object Loading : ProfileEditUiState
    data class Error(val message: String) : ProfileEditUiState

    /**
     * @param nicknameError 저장 시 `409 NICKNAME_TAKEN`을 받으면 채워지는 인라인 에러(닉네임
     *   필드 바로 아래 표시 — 온보딩 S-04 프로필 설정 화면과 같은 톤).
     * @param generalError 그 외 저장 실패(네트워크 오류 등) 시 표시하는 일반 에러 메시지.
     */
    data class Success(
        val nickname: String,
        val selectedAvatarId: String,
        val isSaving: Boolean = false,
        val nicknameError: String? = null,
        val generalError: String? = null,
    ) : ProfileEditUiState {
        val canSave: Boolean get() = nickname.isNotBlank() && !isSaving
    }
}

sealed interface ProfileEditNavigationEvent {
    data object Saved : ProfileEditNavigationEvent
}

/**
 * 프로필 수정 (2026-09-16 신규, 사용자 지시 — v1.3 SPEC 밖, `PATCH /users/me`). 초기값은 이
 * ViewModel이 직접 `GET /users/me`를 호출해 채운다(네비게이션 인자로 넘기지 않음 — 마이(S-42)가
 * 이미 들고 있는 값을 다시 직렬화해 넘기는 것보다 단순하다). 저장은 닉네임/아바타 중 바뀐 것만
 * 골라 보내지 않고 항상 둘 다 보낸다(diff 로직을 두지 않는다 — 과설계 방지, 서버도 동일 값
 * 재전송을 허용한다).
 */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val userApi: UserApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileEditUiState>(ProfileEditUiState.Loading)
    val uiState: StateFlow<ProfileEditUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<ProfileEditNavigationEvent>()
    val navigationEvents: SharedFlow<ProfileEditNavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ProfileEditUiState.Loading
            _uiState.value = when (val result = safeApiCall { userApi.getMe() }) {
                is UiResult.Success -> ProfileEditUiState.Success(
                    nickname = result.data.nickname,
                    selectedAvatarId = result.data.avatarId,
                )
                is UiResult.Error -> ProfileEditUiState.Error(result.message)
                UiResult.Loading -> ProfileEditUiState.Loading
            }
        }
    }

    fun onNicknameChange(value: String) {
        val state = _uiState.value
        if (state !is ProfileEditUiState.Success) return
        _uiState.value = state.copy(nickname = value, nicknameError = null, generalError = null)
    }

    fun onAvatarSelected(avatarId: String) {
        val state = _uiState.value
        if (state !is ProfileEditUiState.Success) return
        _uiState.value = state.copy(selectedAvatarId = avatarId)
    }

    fun onSaveClick() {
        val state = _uiState.value
        if (state !is ProfileEditUiState.Success || !state.canSave) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, nicknameError = null, generalError = null)
            val request = PatchMeRequest(nickname = state.nickname.trim(), avatarId = state.selectedAvatarId)
            when (val result = safeApiCall { userApi.patchMe(request) }) {
                is UiResult.Success -> _navigationEvents.emit(ProfileEditNavigationEvent.Saved)
                is UiResult.Error -> {
                    val current = _uiState.value as? ProfileEditUiState.Success ?: return@launch
                    _uiState.value = if (result.code == "NICKNAME_TAKEN") {
                        current.copy(isSaving = false, nicknameError = "이미 사용 중인 닉네임이에요.")
                    } else {
                        current.copy(isSaving = false, generalError = result.message)
                    }
                }
                UiResult.Loading -> Unit
            }
        }
    }
}
