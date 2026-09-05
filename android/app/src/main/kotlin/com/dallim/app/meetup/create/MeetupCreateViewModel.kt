package com.dallim.app.meetup.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.meetup.MeetupFormat
import com.dallim.app.navigation.DallimDestinations
import com.dallim.network.meetup.CreateMeetupRequest
import com.dallim.network.meetup.MeetupApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** docs/02-api-spec.md 14.1/14.3, docs/01-feature-spec.md §1.8: 정원 2~20명. */
private const val MIN_PARTICIPANTS = 2
private const val MAX_PARTICIPANTS = 20

/**
 * S-48 폼 상태. 날짜/시간은 각각 선택 전엔 `null`이라 필드별 미입력 상태를 구분할 수 있다.
 * 여기서 계산하는 에러들은 전부 **UX 프리뷰 전용 최소 검증**이다 — 완주 판정 원칙과 동일하게,
 * 최종 성공/실패는 항상 서버 응답(`POST /routes/{routeId}/meetups`)이 결정한다
 * (docs/02-api-spec.md 14.3: `scheduledAt` 과거 시각 -> `400 VALIDATION_ERROR`,
 * `maxParticipants` 2~20 범위 밖 -> `400 VALIDATION_ERROR`).
 */
data class MeetupCreateUiState(
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val maxParticipantsInput: String = "",
    val description: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    private val dateTime: LocalDateTime?
        get() = if (date != null && time != null) LocalDateTime.of(date, time) else null

    /** 14.1: "미래 시각만 허용". 둘 다 선택되기 전엔 아직 판단할 수 없으므로 에러를 보이지 않는다. */
    val dateTimeError: String?
        get() = dateTime?.let { if (it.isBefore(LocalDateTime.now())) "지난 시각은 선택할 수 없어요." else null }

    private val maxParticipants: Int?
        get() = maxParticipantsInput.toIntOrNull()

    val maxParticipantsError: String?
        get() = when {
            maxParticipantsInput.isEmpty() -> null
            maxParticipants == null -> "숫자만 입력해주세요."
            maxParticipants !in MIN_PARTICIPANTS..MAX_PARTICIPANTS ->
                "정원은 ${MIN_PARTICIPANTS}~${MAX_PARTICIPANTS}명 사이로 정해주세요."
            else -> null
        }

    val canSubmit: Boolean
        get() = !isSubmitting &&
            dateTime != null && dateTimeError == null &&
            maxParticipants != null && maxParticipantsError == null
}

sealed interface MeetupCreateNavigationEvent {
    /** 생성 성공 — S-47로 돌아가면서 목록을 새로고침해야 함을 알리는 신호. */
    data object Created : MeetupCreateNavigationEvent
}

/**
 * S-48 모집 만들기 (docs/01-feature-spec.md §1.8, docs/02-api-spec.md 14.3).
 * [MeetupListViewModel]과 동일한 상태 홀더 패턴(StateFlow UI 상태 + `safeApiCall` 기반 에러 처리)을
 * 따르되, 폼 화면이라 로딩/성공/에러를 하나의 sealed 상태 대신 단일 데이터 클래스의 필드로 표현한다
 * ([EmailAuthViewModel]과 동일한 폼 화면 관례).
 */
@HiltViewModel
class MeetupCreateViewModel @Inject constructor(
    private val meetupApi: MeetupApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routeId: String =
        checkNotNull(savedStateHandle[DallimDestinations.ARG_ROUTE_ID]) { "routeId 인자가 없습니다." }

    private val _uiState = MutableStateFlow(MeetupCreateUiState())
    val uiState: StateFlow<MeetupCreateUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<MeetupCreateNavigationEvent>()
    val navigationEvents: SharedFlow<MeetupCreateNavigationEvent> = _navigationEvents.asSharedFlow()

    fun onDateSelected(date: LocalDate) {
        _uiState.value = _uiState.value.copy(date = date, errorMessage = null)
    }

    fun onTimeSelected(time: LocalTime) {
        _uiState.value = _uiState.value.copy(time = time, errorMessage = null)
    }

    fun onMaxParticipantsChange(value: String) {
        if (value.isNotEmpty() && (value.length > 3 || value.any { !it.isDigit() })) return
        _uiState.value = _uiState.value.copy(maxParticipantsInput = value, errorMessage = null)
    }

    fun onDescriptionChange(value: String) {
        _uiState.value = _uiState.value.copy(description = value, errorMessage = null)
    }

    fun onSubmit() {
        val state = _uiState.value
        val date = state.date
        val time = state.time
        val maxParticipants = state.maxParticipantsInput.toIntOrNull()
        if (!state.canSubmit || date == null || time == null || maxParticipants == null) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
            val request = CreateMeetupRequest(
                scheduledAt = MeetupFormat.toIsoInstant(
                    year = date.year,
                    month = date.monthValue,
                    day = date.dayOfMonth,
                    hour = time.hour,
                    minute = time.minute,
                ),
                maxParticipants = maxParticipants,
                description = state.description.trim().ifEmpty { null },
            )
            // 완주 판정과 동일한 원칙: 클라이언트 검증(canSubmit)을 통과했어도 최종 판단은
            // 서버 응답(safeApiCall 결과)이 내린다.
            when (val result = safeApiCall { meetupApi.createMeetup(routeId, request) }) {
                is UiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                    _navigationEvents.emit(MeetupCreateNavigationEvent.Created)
                }
                is UiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = result.message)
                }
                UiResult.Loading -> Unit
            }
        }
    }
}
