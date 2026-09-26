package com.dallim.app.race.trainingplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.race.RaceApi
import com.dallim.network.trainingplan.TrainingPlanApi
import com.dallim.network.trainingplan.TrainingPlanGenerateRequestBody
import com.dallim.network.trainingplan.TrainingPlanResponseBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TrainingPlanUiState {
    data object Loading : TrainingPlanUiState

    /**
     * 대회 종목이 여럿이라 `category`를 명시해야 하는 상태(`400/422
     * TRAINING_PLAN_CATEGORY_REQUIRED`/`TRAINING_PLAN_CATEGORY_NOT_OFFERED`). [categories]는
     * `GET /races/{raceId}` 상세를 다시 불러와 채운다 — 이 화면 진입 시점엔 종목 목록을 모른다.
     */
    data class NeedsCategory(
        val categories: List<String>,
        val isSubmitting: Boolean = false,
        val errorMessage: String? = null,
    ) : TrainingPlanUiState

    /** `status == PENDING` — 워커가 아직 만드는 중. [startPolling]이 몇 초 간격으로 GET을 다시 부른다. */
    data class Generating(val category: String) : TrainingPlanUiState

    /** 폴링이 [MAX_POLL_ATTEMPTS]를 넘겨도 여전히 PENDING — 워커가 죽었을 가능성. 수동 새로고침만 제공. */
    data class TimedOut(val category: String) : TrainingPlanUiState

    data class Ready(val plan: TrainingPlanResponseBody) : TrainingPlanUiState

    /** `status == FAILED` — [errorMessage]는 운영자용이라 화면엔 고정 문구만 보여준다(19.3). */
    data class Failed(val category: String, val errorMessage: String?) : TrainingPlanUiState

    /** `TRAINING_PLAN_RACE_NOT_SAVED` 등 재시도해도 같은 결과가 나올 일반 오류. */
    data class Error(val message: String) : TrainingPlanUiState
}

/**
 * S-86 대회 목표 훈련 플랜 (docs/02-api-spec.md 19장, docs/01-feature-spec.md 1.10.3). 결제 게이트
 * 없음(CLAUDE.md 2026-09-18 결정) — 로그인만 하면 누구나 진입 가능, 별도 구독 체크를 하지 않는다.
 *
 * 흐름: 진입 시 `GET`을 먼저 시도 → `404 TRAINING_PLAN_NOT_FOUND`면 `POST`로 생성 요청 →
 * `PENDING`이면 화면이 떠 있는 동안([onCleared]에서 취소) 폴링해서 `READY`/`FAILED` 전환을 감지.
 * `POST`가 `TRAINING_PLAN_CATEGORY_REQUIRED`/`NOT_OFFERED`로 거절하면 대회 상세를 다시 불러와
 * 종목 선택 UI로 전환한다.
 */
@HiltViewModel
class TrainingPlanViewModel @Inject constructor(
    private val trainingPlanApi: TrainingPlanApi,
    private val raceApi: RaceApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val raceId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_RACE_ID]) { "raceId 인자가 없습니다." }

    private val _uiState = MutableStateFlow<TrainingPlanUiState>(TrainingPlanUiState.Loading)
    val uiState: StateFlow<TrainingPlanUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        load()
    }

    /** 최초 진입 + "다시 시도"(일반 오류) 공용 진입점. */
    fun load() {
        pollingJob?.cancel()
        viewModelScope.launch {
            _uiState.value = TrainingPlanUiState.Loading
            when (val result = safeApiCall { trainingPlanApi.getTrainingPlan(raceId) }) {
                is UiResult.Success -> applyPlan(result.data, startPollingIfPending = true)
                is UiResult.Error -> {
                    if (result.code == CODE_NOT_FOUND) {
                        requestGeneration(category = null)
                    } else {
                        _uiState.value = TrainingPlanUiState.Error(result.message)
                    }
                }
                UiResult.Loading -> Unit
            }
        }
    }

    /** 종목 선택 화면(NeedsCategory)에서 유저가 종목을 고른 뒤 호출. */
    fun onCategorySelected(category: String) {
        requestGeneration(category)
    }

    /** FAILED 플랜의 "다시 시도" — 이미 확정된 종목으로 재요청한다(19.2: FAILED만 PENDING으로 리셋). */
    fun onRetryFailedClick() {
        val current = _uiState.value as? TrainingPlanUiState.Failed ?: return
        requestGeneration(current.category)
    }

    /** TimedOut 상태의 수동 새로고침 — 새 job을 발행하지 않고 그냥 다시 GET한다. */
    fun onRefreshClick() {
        load()
    }

    private fun requestGeneration(category: String?) {
        val prev = _uiState.value
        viewModelScope.launch {
            if (prev is TrainingPlanUiState.NeedsCategory) {
                _uiState.value = prev.copy(isSubmitting = true, errorMessage = null)
            } else {
                _uiState.value = TrainingPlanUiState.Loading
            }

            when (val result = safeApiCall { trainingPlanApi.requestGeneration(raceId, TrainingPlanGenerateRequestBody(category)) }) {
                is UiResult.Success -> applyPlan(result.data, startPollingIfPending = true)
                is UiResult.Error -> when (result.code) {
                    CODE_CATEGORY_REQUIRED, CODE_CATEGORY_NOT_OFFERED -> loadCategoriesForSelection(result.message)
                    else -> _uiState.value = TrainingPlanUiState.Error(result.message)
                }
                UiResult.Loading -> Unit
            }
        }
    }

    private suspend fun loadCategoriesForSelection(errorMessage: String) {
        when (val result = safeApiCall { raceApi.getRaceDetail(raceId) }) {
            is UiResult.Success -> _uiState.value = TrainingPlanUiState.NeedsCategory(
                categories = result.data.categories.map { it.category },
                errorMessage = errorMessage,
            )
            is UiResult.Error -> _uiState.value = TrainingPlanUiState.Error(result.message)
            UiResult.Loading -> Unit
        }
    }

    private fun applyPlan(plan: TrainingPlanResponseBody, startPollingIfPending: Boolean) {
        _uiState.value = when (plan.status) {
            STATUS_READY -> TrainingPlanUiState.Ready(plan)
            STATUS_FAILED -> TrainingPlanUiState.Failed(category = plan.category, errorMessage = plan.errorMessage)
            else -> TrainingPlanUiState.Generating(category = plan.category)
        }
        if (plan.status == STATUS_PENDING && startPollingIfPending) {
            startPolling()
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            var attempts = 0
            while (attempts < MAX_POLL_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)
                attempts++
                when (val result = safeApiCall { trainingPlanApi.getTrainingPlan(raceId) }) {
                    is UiResult.Success -> {
                        applyPlan(result.data, startPollingIfPending = false)
                        if (result.data.status != STATUS_PENDING) return@launch
                    }
                    // 네트워크 일시 오류는 폴링을 끊지 않고 다음 시도를 기다린다.
                    is UiResult.Error, UiResult.Loading -> Unit
                }
            }
            val stillPending = _uiState.value as? TrainingPlanUiState.Generating ?: return@launch
            _uiState.value = TrainingPlanUiState.TimedOut(stillPending.category)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    private companion object {
        const val CODE_NOT_FOUND = "TRAINING_PLAN_NOT_FOUND"
        const val CODE_CATEGORY_REQUIRED = "TRAINING_PLAN_CATEGORY_REQUIRED"
        const val CODE_CATEGORY_NOT_OFFERED = "TRAINING_PLAN_CATEGORY_NOT_OFFERED"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"

        const val POLL_INTERVAL_MS = 3000L
        // 3초 간격 x 10회 = 최대 30초 대기(작업 브리핑 지시 "30초+면 타임아웃 안내").
        const val MAX_POLL_ATTEMPTS = 10
    }
}
