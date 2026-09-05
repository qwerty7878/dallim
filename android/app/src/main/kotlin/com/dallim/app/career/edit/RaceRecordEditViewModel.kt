package com.dallim.app.career.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.career.RaceCategoryOption
import com.dallim.app.career.RaceRecordFormState
import com.dallim.app.career.RecordTypeOption
import com.dallim.app.career.sanitizedDigitsOrNull
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.navigation.DallimDestinations
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

sealed interface RaceRecordEditUiState {
    data object Loading : RaceRecordEditUiState

    data class Ready(
        val isEditMode: Boolean,
        val form: RaceRecordFormState = RaceRecordFormState(),
        val isSubmitting: Boolean = false,
        val isDeleting: Boolean = false,
        val errorMessage: String? = null,
        val showDeleteConfirm: Boolean = false,
    ) : RaceRecordEditUiState

    data class LoadError(val message: String) : RaceRecordEditUiState
}

sealed interface RaceRecordEditNavigationEvent {
    data object Saved : RaceRecordEditNavigationEvent
    data object Deleted : RaceRecordEditNavigationEvent
}

/**
 * S-90 완주 이력 등록/편집 — docs/02-api-spec.md 15.3/15.4/15.5. `raceRecordId` 인자가 없으면
 * 등록 모드, 있으면 수정 모드.
 *
 * 수정 모드는 **단건 조회 API가 없어**(15장에는 `GET /users/me/race-records`(목록)만 있고
 * `GET .../{id}`가 없다) 목록을 다시 불러와 id로 찾는다 — 유저 1인당 이력 수가 적어(15.2
 * "페이지네이션 없음" 근거와 동일한 전제) 이 방식의 비용이 무시할 만하다고 판단했다.
 */
@HiltViewModel
class RaceRecordEditViewModel @Inject constructor(
    private val raceRecordApi: RaceRecordApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val raceRecordId: String? = savedStateHandle[DallimDestinations.ARG_RACE_RECORD_ID]
    val isEditMode: Boolean = raceRecordId != null

    private val _uiState = MutableStateFlow<RaceRecordEditUiState>(
        if (isEditMode) RaceRecordEditUiState.Loading else RaceRecordEditUiState.Ready(isEditMode = false),
    )
    val uiState: StateFlow<RaceRecordEditUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<RaceRecordEditNavigationEvent>()
    val navigationEvents: SharedFlow<RaceRecordEditNavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        if (isEditMode) loadExisting()
    }

    fun retryLoad() {
        if (isEditMode) loadExisting()
    }

    private fun loadExisting() {
        viewModelScope.launch {
            _uiState.value = RaceRecordEditUiState.Loading
            when (val result = safeApiCall { raceRecordApi.getRaceRecords() }) {
                is UiResult.Success -> {
                    val item = result.data.items.firstOrNull { it.id == raceRecordId }
                    _uiState.value = if (item != null) {
                        RaceRecordEditUiState.Ready(isEditMode = true, form = RaceRecordFormState.fromItem(item))
                    } else {
                        RaceRecordEditUiState.LoadError("완주 이력을 찾을 수 없어요.")
                    }
                }
                is UiResult.Error -> _uiState.value = RaceRecordEditUiState.LoadError(result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    private inline fun updateReady(block: (RaceRecordEditUiState.Ready) -> RaceRecordEditUiState.Ready) {
        (_uiState.value as? RaceRecordEditUiState.Ready)?.let { _uiState.value = block(it) }
    }

    private inline fun updateForm(block: (RaceRecordFormState) -> RaceRecordFormState) {
        updateReady { it.copy(form = block(it.form), errorMessage = null) }
    }

    fun onRaceNameChange(value: String) = updateForm { it.copy(raceName = value) }
    fun onCategorySelected(option: RaceCategoryOption) = updateForm { it.copy(category = option) }
    fun onOtherDistanceChange(value: String) = updateForm { it.copy(otherDistanceKmInput = value) }

    fun onYearChange(value: String) {
        if (sanitizedDigitsOrNull(value, 4) == null) return
        updateForm { it.copy(yearInput = value) }
    }

    fun onHoursChange(value: String) = updateForm { it.copy(hoursInput = value) }
    fun onMinutesChange(value: String) = updateForm { it.copy(minutesInput = value) }
    fun onSecondsChange(value: String) = updateForm { it.copy(secondsInput = value) }
    fun onRecordTypeSelected(option: RecordTypeOption?) = updateForm { it.copy(recordType = option) }
    fun onBibNumberChange(value: String) = updateForm { it.copy(bibNumber = value) }
    fun onMemoChange(value: String) = updateForm { it.copy(memo = value) }

    fun onSubmit() {
        val current = _uiState.value as? RaceRecordEditUiState.Ready ?: return
        if (!current.form.isValid || current.isSubmitting || current.isDeleting) return

        viewModelScope.launch {
            updateReady { it.copy(isSubmitting = true, errorMessage = null) }
            val result = if (current.isEditMode) {
                safeApiCall { raceRecordApi.updateRaceRecord(checkNotNull(raceRecordId), current.form.toUpdateRequest()) }
            } else {
                safeApiCall { raceRecordApi.createRaceRecord(current.form.toCreateRequest()) }
            }
            when (result) {
                is UiResult.Success -> _navigationEvents.emit(RaceRecordEditNavigationEvent.Saved)
                is UiResult.Error -> updateReady { it.copy(isSubmitting = false, errorMessage = result.message) }
                UiResult.Loading -> Unit
            }
        }
    }

    fun onDeleteClick() = updateReady { it.copy(showDeleteConfirm = true) }
    fun onDismissDeleteConfirm() = updateReady { it.copy(showDeleteConfirm = false) }

    fun onConfirmDelete() {
        val current = _uiState.value as? RaceRecordEditUiState.Ready ?: return
        if (!current.isEditMode || current.isDeleting) return

        viewModelScope.launch {
            updateReady { it.copy(isDeleting = true, showDeleteConfirm = false, errorMessage = null) }
            when (val result = safeApiCall { raceRecordApi.deleteRaceRecord(checkNotNull(raceRecordId)) }) {
                is UiResult.Success -> _navigationEvents.emit(RaceRecordEditNavigationEvent.Deleted)
                is UiResult.Error -> updateReady { it.copy(isDeleting = false, errorMessage = result.message) }
                UiResult.Loading -> Unit
            }
        }
    }
}
