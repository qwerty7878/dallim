package com.dallim.app.social.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dallim.app.common.UiResult
import com.dallim.app.common.safeApiCall
import com.dallim.app.social.SocialSessionFormat
import com.dallim.network.route.RouteApi
import com.dallim.network.route.RouteListItem
import com.dallim.network.social.CreateSocialSessionRequest
import com.dallim.network.social.SocialSessionApi
import com.dallim.network.social.SocialSessionGenderCondition
import com.dallim.network.social.SocialSessionRainPolicy
import com.dallim.ui.components.GeoPoint
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

/** docs/02-api-spec.md 17.2/17.3, backend SocialSessionService.create(): 1~30명. */
private const val MIN_PARTICIPANTS_FLOOR = 1
private const val MAX_PARTICIPANTS_CEILING = 30

/**
 * S-31 세션 생성 폼 상태 — 완주 판정과 동일한 원칙으로, 여기서 계산하는 에러는 전부 UX 프리뷰
 * 전용 최소 검증이다. 최종 성공/실패는 항상 서버 응답(`POST /social-sessions`)이 결정한다
 * (SocialSessionCreateViewModel.onSubmit 참고). SPEC에 없는 "목표 페이스"는 만들지 않는다 —
 * `runningStyles` 자유 텍스트로 대체(작업 브리핑 참고).
 */
data class SocialSessionCreateUiState(
    val selectedRoute: RouteListItem? = null,
    val isRoutePickerOpen: Boolean = false,
    val isLoadingRoutes: Boolean = false,
    val routeOptions: List<RouteListItem> = emptyList(),
    val routePickerErrorMessage: String? = null,
    val title: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val selectedRunningStyles: List<String> = emptyList(),
    val customStyleInput: String = "",
    val minParticipantsInput: String = "",
    val maxParticipantsInput: String = "",
    val beginnerFriendly: Boolean = false,
    val minTemperatureInput: String = "",
    val genderCondition: String = SocialSessionGenderCondition.ANY,
    val description: String = "",
    val meetingPoint: GeoPoint? = null,
    val meetingPointDescription: String = "",
    val rainPolicy: String = SocialSessionRainPolicy.DECIDE_LATER,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    private val dateTime: LocalDateTime?
        get() = if (date != null && time != null) LocalDateTime.of(date, time) else null

    val dateTimeError: String?
        get() = dateTime?.let { if (it.isBefore(LocalDateTime.now())) "지난 시각은 선택할 수 없어요." else null }

    private val minParticipants: Int?
        get() = minParticipantsInput.toIntOrNull()
    private val maxParticipants: Int?
        get() = maxParticipantsInput.toIntOrNull()

    /** 17.3: `minParticipants < 1` / `maxParticipants < minParticipants` / `maxParticipants > 30`
     * 이면 서버가 400을 준다 — 동일 조건을 미리 프리뷰로 보여준다. */
    val participantsError: String?
        get() {
            if (minParticipantsInput.isEmpty() && maxParticipantsInput.isEmpty()) return null
            val min = minParticipants
            val max = maxParticipants
            return when {
                min == null || max == null -> "숫자만 입력해주세요."
                min < MIN_PARTICIPANTS_FLOOR -> "최소 인원은 ${MIN_PARTICIPANTS_FLOOR}명 이상이어야 해요."
                max < min -> "최대 인원은 최소 인원보다 적을 수 없어요."
                max > MAX_PARTICIPANTS_CEILING -> "최대 인원은 ${MAX_PARTICIPANTS_CEILING}명을 넘을 수 없어요."
                else -> null
            }
        }

    val minTemperature: Double?
        get() = minTemperatureInput.toDoubleOrNull()

    val minTemperatureError: String?
        get() = if (minTemperatureInput.isNotEmpty() && minTemperature == null) "숫자만 입력해주세요." else null

    val canSubmit: Boolean
        get() = !isSubmitting &&
            selectedRoute != null &&
            title.isNotBlank() &&
            dateTime != null && dateTimeError == null &&
            minParticipants != null && maxParticipants != null && participantsError == null &&
            minTemperatureError == null &&
            meetingPoint != null
}

sealed interface SocialSessionCreateNavigationEvent {
    data object Created : SocialSessionCreateNavigationEvent
}

/** S-31 러닝 스타일 추천 4종 — backend SocialSessionTable.runningStyles 문서 주석의 예시.
 * 자유 텍스트 컬럼이라 강제 값 목록이 아니라 "추천 칩"일 뿐, [SocialSessionCreateScreen]에서
 * 직접 입력으로 추가한 커스텀 값도 동일하게 취급한다. */
val SUGGESTED_RUNNING_STYLES = listOf("대화하면서", "페이스러닝", "땀나게", "관광하며")

/**
 * S-31 세션 생성 (docs/02-api-spec.md 17.3). [com.dallim.app.meetup.create.MeetupCreateViewModel]
 * 과 동일한 상태 홀더 패턴(단일 데이터 클래스 폼 상태 + `safeApiCall`).
 */
@HiltViewModel
class SocialSessionCreateViewModel @Inject constructor(
    private val socialSessionApi: SocialSessionApi,
    private val routeApi: RouteApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialSessionCreateUiState())
    val uiState: StateFlow<SocialSessionCreateUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<SocialSessionCreateNavigationEvent>()
    val navigationEvents: SharedFlow<SocialSessionCreateNavigationEvent> = _navigationEvents.asSharedFlow()

    fun onOpenRoutePicker() {
        _uiState.value = _uiState.value.copy(isRoutePickerOpen = true)
        if (_uiState.value.routeOptions.isEmpty()) loadRoutes()
    }

    fun onDismissRoutePicker() {
        _uiState.value = _uiState.value.copy(isRoutePickerOpen = false)
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRoutes = true, routePickerErrorMessage = null)
            when (val result = safeApiCall { routeApi.getRoutes(size = 50) }) {
                is UiResult.Success -> _uiState.value =
                    _uiState.value.copy(isLoadingRoutes = false, routeOptions = result.data.items)
                is UiResult.Error -> _uiState.value =
                    _uiState.value.copy(isLoadingRoutes = false, routePickerErrorMessage = result.message)
                UiResult.Loading -> Unit
            }
        }
    }

    fun onRouteSelected(route: RouteListItem) {
        _uiState.value = _uiState.value.copy(
            selectedRoute = route,
            isRoutePickerOpen = false,
            errorMessage = null,
        )
    }

    fun onTitleChange(value: String) {
        _uiState.value = _uiState.value.copy(title = value, errorMessage = null)
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.value = _uiState.value.copy(date = date, errorMessage = null)
    }

    fun onTimeSelected(time: LocalTime) {
        _uiState.value = _uiState.value.copy(time = time, errorMessage = null)
    }

    fun onToggleRunningStyle(style: String) {
        val current = _uiState.value.selectedRunningStyles
        val updated = if (style in current) current - style else current + style
        _uiState.value = _uiState.value.copy(selectedRunningStyles = updated)
    }

    fun onCustomStyleInputChange(value: String) {
        _uiState.value = _uiState.value.copy(customStyleInput = value)
    }

    fun onAddCustomStyle() {
        val custom = _uiState.value.customStyleInput.trim()
        if (custom.isEmpty() || custom in _uiState.value.selectedRunningStyles) return
        _uiState.value = _uiState.value.copy(
            selectedRunningStyles = _uiState.value.selectedRunningStyles + custom,
            customStyleInput = "",
        )
    }

    fun onMinParticipantsChange(value: String) {
        if (value.isNotEmpty() && (value.length > 2 || value.any { !it.isDigit() })) return
        _uiState.value = _uiState.value.copy(minParticipantsInput = value, errorMessage = null)
    }

    fun onMaxParticipantsChange(value: String) {
        if (value.isNotEmpty() && (value.length > 2 || value.any { !it.isDigit() })) return
        _uiState.value = _uiState.value.copy(maxParticipantsInput = value, errorMessage = null)
    }

    fun onBeginnerFriendlyToggle(value: Boolean) {
        _uiState.value = _uiState.value.copy(beginnerFriendly = value)
    }

    fun onMinTemperatureChange(value: String) {
        if (value.isNotEmpty() && value.any { !it.isDigit() && it != '.' }) return
        _uiState.value = _uiState.value.copy(minTemperatureInput = value, errorMessage = null)
    }

    fun onGenderConditionSelected(value: String) {
        _uiState.value = _uiState.value.copy(genderCondition = value)
    }

    fun onDescriptionChange(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
    }

    fun onMeetingPointPicked(point: GeoPoint) {
        _uiState.value = _uiState.value.copy(meetingPoint = point, errorMessage = null)
    }

    fun onMeetingPointDescriptionChange(value: String) {
        _uiState.value = _uiState.value.copy(meetingPointDescription = value)
    }

    fun onRainPolicySelected(value: String) {
        _uiState.value = _uiState.value.copy(rainPolicy = value)
    }

    fun onSubmit() {
        val state = _uiState.value
        val route = state.selectedRoute
        val date = state.date
        val time = state.time
        val minParticipants = state.minParticipantsInput.toIntOrNull()
        val maxParticipants = state.maxParticipantsInput.toIntOrNull()
        val meetingPoint = state.meetingPoint
        if (!state.canSubmit || route == null || date == null || time == null ||
            minParticipants == null || maxParticipants == null || meetingPoint == null
        ) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
            val request = CreateSocialSessionRequest(
                routeId = route.routeId,
                title = state.title.trim(),
                scheduledAt = SocialSessionFormat.toIsoInstant(
                    year = date.year,
                    month = date.monthValue,
                    day = date.dayOfMonth,
                    hour = time.hour,
                    minute = time.minute,
                ),
                minParticipants = minParticipants,
                maxParticipants = maxParticipants,
                runningStyles = state.selectedRunningStyles,
                beginnerFriendly = state.beginnerFriendly,
                minRunningTemperature = state.minTemperature,
                genderCondition = state.genderCondition,
                description = state.description.trim().ifEmpty { null },
                meetingPointLat = meetingPoint.lat,
                meetingPointLng = meetingPoint.lng,
                meetingPointDescription = state.meetingPointDescription.trim().ifEmpty { null },
                rainPolicy = state.rainPolicy,
            )
            // 완주 판정과 동일한 원칙: 클라이언트 검증(canSubmit)을 통과했어도 최종 판단은 서버
            // 응답이 내린다 — 완주 0회 유저는 여기서 400 SESSION_HOST_REQUIRES_FIRST_RUN을 받고,
            // 서버가 이미 "한 번이라도 달려본 뒤 열 수 있어요."를 message로 내려주므로 클라이언트가
            // 별도로 문구를 다시 만들지 않고 result.message를 그대로 노출한다.
            when (val result = safeApiCall { socialSessionApi.createSocialSession(request) }) {
                is UiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                    _navigationEvents.emit(SocialSessionCreateNavigationEvent.Created)
                }
                is UiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = result.message)
                }
                UiResult.Loading -> Unit
            }
        }
    }
}
