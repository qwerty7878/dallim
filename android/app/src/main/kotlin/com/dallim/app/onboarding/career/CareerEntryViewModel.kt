package com.dallim.app.onboarding.career

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.career.RaceCategoryOption
import com.dallim.app.career.RaceRecordFormState
import com.dallim.app.career.sanitizedDigitsOrNull
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.racerecord.PaceSuggestionRequest
import com.dallim.network.racerecord.RaceRecordApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CareerEntryStep { QUESTION, FORM }

/**
 * S-04b UI 상태. [pendingPaceSuggestion]은 하프/풀 기록 입력 후 [suggestPace] 호출 결과가
 * S-04에서 고른 페이스와 다를 때만 채워져 배너를 띄운다 — 확인 전까지는 저장을 진행하지 않는다.
 */
data class CareerEntryUiState(
    val step: CareerEntryStep = CareerEntryStep.QUESTION,
    val form: RaceRecordFormState = RaceRecordFormState(),
    val isSubmitting: Boolean = false,
    val pendingPaceSuggestion: String? = null,
    val errorMessage: String? = null,
)

sealed interface CareerEntryNavigationEvent {
    /** [완료]/[아직 없어요]/[건너뛰기] 전부 결국 이 이벤트로 S-05(권한 요청)에 도달한다. */
    data object Finished : CareerEntryNavigationEvent
}

/**
 * S-04b 러닝 커리어 입력 (docs/달림_화면별_상세기획서_v1.3.md PART 3-A, docs/02-api-spec.md
 * 15장). S-04에서 `runningExperience == OVER_1_YEAR`를 선택했을 때만 진입한다(네비게이션
 * 분기는 `ProfileSetupViewModel` 쪽에서 처리).
 *
 * 페이스 자동 반영: 하프/풀 기록이 입력된 상태로 [onSubmit]하면 `POST
 * /users/me/race-records/pace-suggestion`으로 예상 페이스를 미리 물어보고, S-04에서 고른 값과
 * 다르면 배너로 보여준다. **프로필 페이스를 실제로 갱신하는 API(`PATCH /users/me` 등)는
 * 백엔드에 없다** — `backend/src/main/kotlin/com/dallim/user/UserRoutes.kt`에는 `POST
 * /users/me/profile`(최초 등록)과 `GET /users/me`만 있고 수정용 엔드포인트가 없으므로, 이
 * 화면은 제안을 **표시만** 하고 실제 반영 API 호출은 하지 않는다(작업 브리핑 "절대 규칙" —
 * SPEC에 없는 API를 임의로 만들지 않는다).
 */
@HiltViewModel
class CareerEntryViewModel @Inject constructor(
    private val raceRecordApi: RaceRecordApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** S-04에서 사용자가 실제로 고른 편안한 페이스 — 서버 제안값과 달라야만 배너를 띄우는 데 쓴다. */
    val currentComfortablePaceApiValue: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_COMFORTABLE_PACE]) { "comfortablePace 인자가 없습니다." }

    private val _uiState = MutableStateFlow(CareerEntryUiState())
    val uiState: StateFlow<CareerEntryUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<CareerEntryNavigationEvent>()
    val navigationEvents: SharedFlow<CareerEntryNavigationEvent> = _navigationEvents.asSharedFlow()

    fun onHasExperienceYes() {
        _uiState.value = _uiState.value.copy(step = CareerEntryStep.FORM)
    }

    /** [아직 없어요] — 이력을 저장하지 않고 바로 S-05로. */
    fun onHasExperienceNo() {
        finish()
    }

    /** 상단 [건너뛰기] — 어느 단계에 있든 저장 없이 바로 S-05로(항상 노출). */
    fun onSkip() {
        finish()
    }

    fun onRaceNameChange(value: String) {
        updateForm { it.copy(raceName = value) }
    }

    fun onCategorySelected(option: RaceCategoryOption) {
        updateForm { it.copy(category = option) }
    }

    fun onOtherDistanceChange(value: String) {
        updateForm { it.copy(otherDistanceKmInput = value) }
    }

    fun onYearChange(value: String) {
        if (sanitizedDigitsOrNull(value, 4) == null) return
        updateForm { it.copy(yearInput = value) }
    }

    fun onHoursChange(value: String) = updateForm { it.copy(hoursInput = value) }
    fun onMinutesChange(value: String) = updateForm { it.copy(minutesInput = value) }
    fun onSecondsChange(value: String) = updateForm { it.copy(secondsInput = value) }

    private inline fun updateForm(block: (RaceRecordFormState) -> RaceRecordFormState) {
        _uiState.value = _uiState.value.copy(form = block(_uiState.value.form), errorMessage = null)
    }

    /** [완료] — 하프/풀 기록이면 페이스 제안을 먼저 확인하고, 없거나 현재값과 같으면 바로 저장. */
    fun onSubmit() {
        val state = _uiState.value
        if (!state.form.isValid || state.isSubmitting) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
            val category = state.form.category
            val recordSeconds = state.form.recordSecondsOrNull
            if (category?.supportsPaceSuggestion == true && recordSeconds != null) {
                val result = safeApiCall {
                    raceRecordApi.suggestPace(PaceSuggestionRequest(category.apiValue, recordSeconds))
                }
                if (result is UiResult.Success && result.data.suggestedPace != currentComfortablePaceApiValue) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        pendingPaceSuggestion = result.data.suggestedPace,
                    )
                    return@launch
                }
            }
            saveAndFinish()
        }
    }

    /** 페이스 제안 배너의 [확인] — 반영 API가 없어 배너를 닫고 이력 저장을 계속 진행하는 것뿐이다. */
    fun onAcknowledgePaceSuggestion() {
        _uiState.value = _uiState.value.copy(pendingPaceSuggestion = null)
        viewModelScope.launch { saveAndFinish() }
    }

    private suspend fun saveAndFinish() {
        val form = _uiState.value.form
        _uiState.value = _uiState.value.copy(isSubmitting = true)
        when (val result = safeApiCall { raceRecordApi.createRaceRecord(form.toCreateRequest()) }) {
            is UiResult.Success -> finish()
            is UiResult.Error -> _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = result.message)
            UiResult.Loading -> Unit
        }
    }

    private fun finish() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = false)
            _navigationEvents.emit(CareerEntryNavigationEvent.Finished)
        }
    }
}
