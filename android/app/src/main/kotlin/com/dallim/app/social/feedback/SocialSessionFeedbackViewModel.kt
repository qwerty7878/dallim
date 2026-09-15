package com.dallim.app.social.feedback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.social.SocialFeedbackTags
import com.dallim.network.social.SocialSessionApi
import com.dallim.network.social.SocialSessionFeedbackTargetItem
import com.dallim.network.social.SubmitSocialSessionFeedbackRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SocialSessionFeedbackUiState {
    data object Loading : SocialSessionFeedbackUiState
    data class Error(val message: String) : SocialSessionFeedbackUiState

    /** 대상(체크인한 상대) 한 명씩 카드로 넘기며 평가한다 — [currentIndex]가 [targets] 범위를
     * 넘으면 [isAllDone]. */
    data class Success(
        val targets: List<SocialSessionFeedbackTargetItem>,
        val currentIndex: Int = 0,
        val selectedTags: Set<String> = emptySet(),
        val wantsToRunAgain: Boolean = false,
        val isSubmitting: Boolean = false,
        val submitErrorMessage: String? = null,
        val mateEstablishedMessage: String? = null,
    ) : SocialSessionFeedbackUiState {
        val currentTarget: SocialSessionFeedbackTargetItem? get() = targets.getOrNull(currentIndex)
        val isAllDone: Boolean get() = currentIndex >= targets.size
    }
}

/**
 * S-38 세션 종료 후 평가 (docs/02-api-spec.md 17.16) — 체크인한 참가자끼리 긍정 태그(최대 3개,
 * 별점 없음) + "다시 같이 뛰고 싶어요"를 대상별로 한 명씩 순회하며 제출한다. `mateEstablished`가
 * true로 오면 축하 메시지를 잠깐 보여준다(S-39 Running Mate 성립).
 */
@HiltViewModel
class SocialSessionFeedbackViewModel @Inject constructor(
    private val socialSessionApi: SocialSessionApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_SOCIAL_SESSION_ID]) { "sessionId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<SocialSessionFeedbackUiState>(SocialSessionFeedbackUiState.Loading)
    val uiState: StateFlow<SocialSessionFeedbackUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SocialSessionFeedbackUiState.Loading
            when (val result = safeApiCall { socialSessionApi.getFeedbackTargets(sessionId) }) {
                is UiResult.Success -> _uiState.value = SocialSessionFeedbackUiState.Success(targets = result.data.items)
                is UiResult.Error -> _uiState.value = SocialSessionFeedbackUiState.Error(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    fun onTagToggle(tag: String) {
        updateSuccess { state ->
            val selected = state.selectedTags
            val next = when {
                tag in selected -> selected - tag
                selected.size >= SocialFeedbackTags.MAX_TAGS -> selected
                else -> selected + tag
            }
            state.copy(selectedTags = next)
        }
    }

    fun onWantsToRunAgainChange(value: Boolean) = updateSuccess { it.copy(wantsToRunAgain = value) }

    /** 현재 대상에 대해 제출하고 다음 대상으로 넘어간다(선택 상태는 대상마다 초기화). */
    fun onSubmitClick() {
        val state = _uiState.value as? SocialSessionFeedbackUiState.Success ?: return
        val target = state.currentTarget ?: return
        if (state.isSubmitting) return

        viewModelScope.launch {
            updateSuccess { it.copy(isSubmitting = true, submitErrorMessage = null) }
            val request = SubmitSocialSessionFeedbackRequest(
                targetUserId = target.userId,
                tags = state.selectedTags.toList(),
                wantsToRunAgain = state.wantsToRunAgain,
            )
            when (val result = safeApiCall { socialSessionApi.submitSocialSessionFeedback(sessionId, request) }) {
                is UiResult.Success -> updateSuccess {
                    it.copy(
                        currentIndex = it.currentIndex + 1,
                        selectedTags = emptySet(),
                        wantsToRunAgain = false,
                        isSubmitting = false,
                        mateEstablishedMessage = if (result.data.mateEstablished) {
                            "${target.nickname}님과 러닝메이트가 됐어요!"
                        } else {
                            null
                        },
                    )
                }
                is UiResult.Error -> updateSuccess { it.copy(isSubmitting = false, submitErrorMessage = result.message) }
                UiResult.Loading -> Unit
            }
        }
    }

    /** 이 사람은 건너뛰고 다음 대상으로 — SPEC에 "평가는 선택"이라는 명시는 없지만 미선택
     * 태그(빈 배열) 제출 자체가 이미 허용되므로, 건너뛰기는 그냥 다음으로 넘기기만 한다(제출
     * 안 함 — 대상이 남아있다는 걸 알려줄 뿐 강제하지 않는다). */
    fun onSkipClick() {
        updateSuccess { it.copy(currentIndex = it.currentIndex + 1, selectedTags = emptySet(), wantsToRunAgain = false) }
    }

    fun onMateEstablishedMessageShown() = updateSuccess { it.copy(mateEstablishedMessage = null) }

    private inline fun updateSuccess(transform: (SocialSessionFeedbackUiState.Success) -> SocialSessionFeedbackUiState.Success) {
        _uiState.update { state -> if (state is SocialSessionFeedbackUiState.Success) transform(state) else state }
    }
}
