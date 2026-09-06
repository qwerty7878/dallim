package com.dallim.app.running.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.run.FeedbackTagsRequestBody
import com.dallim.network.run.RunApi
import com.dallim.network.run.RunDetailResponseBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RunResultUiState {
    data object Loading : RunResultUiState
    data class Error(val message: String) : RunResultUiState
    data class Success(val run: RunDetailResponseBody) : RunResultUiState
}

/**
 * S-25 코스 평가 태그(docs/달림_화면별_상세기획서_v1.3.md 395행, 483~484행 원칙) — 별점 없는
 * 긍정 행동 태그만, 최대 3개, 미선택도 허용. 서버 화이트리스트(docs/02-api-spec.md 5장)와 정확히
 * 같은 6개 문자열이어야 한다.
 */
val FEEDBACK_TAG_OPTIONS = listOf(
    "그림이 잘 보여요",
    "달리기 편해요",
    "신호가 적어요",
    "평지예요",
    "가로등이 밝아요",
    "경치가 좋아요",
)

private const val MAX_FEEDBACK_TAGS = 3

/**
 * S-25 달림 결과 (docs/01-feature-spec.md §1.3) — [RunApi.getRun]으로 서버가 최종 계산한 결과를
 * 조회한다. **완주 판정은 서버가 유일한 신뢰 소스**이므로 S-21에서 계산했던 로컬 프리체크값은
 * 여기서 전혀 참조하지 않고, 이 화면은 오직 서버 응답만 그린다(CLAUDE.md rule 3).
 */
@HiltViewModel
class RunResultViewModel @Inject constructor(
    private val runApi: RunApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val runId: String = checkNotNull(savedStateHandle[DallimDestinations.ARG_RUN_ID]) { "runId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<RunResultUiState>(RunResultUiState.Loading)
    val uiState: StateFlow<RunResultUiState> = _uiState.asStateFlow()

    private val _selectedFeedbackTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedFeedbackTags: StateFlow<Set<String>> = _selectedFeedbackTags.asStateFlow()

    private val _feedbackTagsSubmitted = MutableStateFlow(false)
    val feedbackTagsSubmitted: StateFlow<Boolean> = _feedbackTagsSubmitted.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RunResultUiState.Loading
            _uiState.value = when (val result = safeApiCall { runApi.getRun(runId) }) {
                is UiResult.Success -> RunResultUiState.Success(result.data)
                is UiResult.Error -> RunResultUiState.Error(result.message)
                UiResult.Loading -> RunResultUiState.Loading
            }
        }
    }

    /** 3개까지 다중 선택, 4번째 탭은 그냥 무시(과설계 금지) — 별점/자유 텍스트는 SPEC에 없다. */
    fun toggleFeedbackTag(tag: String) {
        val current = _selectedFeedbackTags.value
        _selectedFeedbackTags.value = when {
            tag in current -> current - tag
            current.size >= MAX_FEEDBACK_TAGS -> current
            else -> current + tag
        }
    }

    /**
     * 실패해도 결과 화면 확인 자체엔 지장이 없어야 하므로(요청사항) 에러를 별도 상태로 노출하지
     * 않는다 — 조용히 무시하고, 성공 시에만 "감사합니다" 상태로 전환한다. idempotent라 재시도해도
     * 안전(CLAUDE.md rule 3과 무관, 완주 판정이 아니라 평가 태그이므로).
     */
    fun submitFeedbackTags() {
        val tags = _selectedFeedbackTags.value.toList()
        viewModelScope.launch {
            when (safeApiCall { runApi.submitFeedbackTags(runId, FeedbackTagsRequestBody(tags = tags)) }) {
                is UiResult.Success -> _feedbackTagsSubmitted.value = true
                is UiResult.Error, UiResult.Loading -> Unit
            }
        }
    }
}
