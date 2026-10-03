package com.dallim.app.social.create

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.BuildConfig
import com.dallim.app.social.SocialSessionFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.route.RouteListItem
import com.dallim.network.social.SocialSessionGenderCondition
import com.dallim.network.social.SocialSessionRainPolicy
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimTopBar
import com.dallim.ui.components.DallimTextField
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.NaverRouteMapView
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.components.SectionHeader
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateDisplayFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
private val TimeDisplayFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

/**
 * S-31 세션 생성 (docs/달림_화면별_상세기획서_v1.3.md PART 3-D, docs/02-api-spec.md 17.3).
 * 완주 0회 유저가 제출하면 서버가 400 `SESSION_HOST_REQUIRES_FIRST_RUN`을 주고, 그 서버 메시지
 * ("한 번이라도 달려본 뒤 열 수 있어요.")를 그대로 하단 에러 문구로 노출한다
 * ([com.dallim.app.meetup.create.MeetupCreateScreen]과 동일 관례).
 */
@Composable
fun SocialSessionCreateRoute(
    onBackClick: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SocialSessionCreateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                SocialSessionCreateNavigationEvent.Created -> onCreated()
            }
        }
    }

    SocialSessionCreateScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onOpenRoutePicker = viewModel::onOpenRoutePicker,
        onDismissRoutePicker = viewModel::onDismissRoutePicker,
        onRouteSelected = viewModel::onRouteSelected,
        onTitleChange = viewModel::onTitleChange,
        onDateSelected = viewModel::onDateSelected,
        onTimeSelected = viewModel::onTimeSelected,
        onToggleRunningStyle = viewModel::onToggleRunningStyle,
        onCustomStyleInputChange = viewModel::onCustomStyleInputChange,
        onAddCustomStyle = viewModel::onAddCustomStyle,
        onMinParticipantsChange = viewModel::onMinParticipantsChange,
        onMaxParticipantsChange = viewModel::onMaxParticipantsChange,
        onBeginnerFriendlyToggle = viewModel::onBeginnerFriendlyToggle,
        onMinTemperatureChange = viewModel::onMinTemperatureChange,
        onGenderConditionSelected = viewModel::onGenderConditionSelected,
        onDescriptionChange = viewModel::onDescriptionChange,
        onMeetingPointPicked = viewModel::onMeetingPointPicked,
        onMeetingPointDescriptionChange = viewModel::onMeetingPointDescriptionChange,
        onRainPolicySelected = viewModel::onRainPolicySelected,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SocialSessionCreateScreen(
    uiState: SocialSessionCreateUiState,
    onBackClick: () -> Unit,
    onOpenRoutePicker: () -> Unit,
    onDismissRoutePicker: () -> Unit,
    onRouteSelected: (RouteListItem) -> Unit,
    onTitleChange: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onToggleRunningStyle: (String) -> Unit,
    onCustomStyleInputChange: (String) -> Unit,
    onAddCustomStyle: () -> Unit,
    onMinParticipantsChange: (String) -> Unit,
    onMaxParticipantsChange: (String) -> Unit,
    onBeginnerFriendlyToggle: (Boolean) -> Unit,
    onMinTemperatureChange: (String) -> Unit,
    onGenderConditionSelected: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMeetingPointPicked: (GeoPoint) -> Unit,
    onMeetingPointDescriptionChange: (String) -> Unit,
    onRainPolicySelected: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        DallimTopBar(title = "세션 만들기", onBackClick = onBackClick)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ScreenHorizontal),
        ) {
            SectionHeader(title = "코스")
            RoutePickerField(route = uiState.selectedRoute, onClick = onOpenRoutePicker)

            SectionHeader(title = "제목", modifier = Modifier.padding(top = Spacing.xl))
            DallimTextField(
                value = uiState.title,
                onValueChange = onTitleChange,
                label = "제목",
                placeholder = "예: 안양천 야간 러닝",
            )

            SectionHeader(title = "날짜·시간", modifier = Modifier.padding(top = Spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                DateTimePickerField(
                    icon = DallimIcons.Calendar,
                    displayText = uiState.date?.format(DateDisplayFormatter),
                    placeholder = "날짜 선택",
                    isError = uiState.dateTimeError != null,
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1.2f),
                )
                DateTimePickerField(
                    icon = DallimIcons.Clock,
                    displayText = uiState.time?.format(TimeDisplayFormatter),
                    placeholder = "시간 선택",
                    isError = uiState.dateTimeError != null,
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }
            uiState.dateTimeError?.let {
                Text(
                    text = it,
                    style = DallimTypography.Caption,
                    color = DallimColors.Error,
                    modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs),
                )
            }

            SectionHeader(title = "러닝 스타일 (선택)", modifier = Modifier.padding(top = Spacing.xl))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                (SUGGESTED_RUNNING_STYLES + uiState.selectedRunningStyles)
                    .distinct()
                    .forEach { style ->
                        DallimFilterChip(
                            label = style,
                            selected = style in uiState.selectedRunningStyles,
                            onClick = { onToggleRunningStyle(style) },
                        )
                    }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DallimTextField(
                    value = uiState.customStyleInput,
                    onValueChange = onCustomStyleInputChange,
                    label = "직접 입력",
                    placeholder = "예: 사진찍으며",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAddCustomStyle) {
                    Icon(imageVector = DallimIcons.Plus, contentDescription = "스타일 추가", tint = DallimColors.Primary)
                }
            }

            SectionHeader(title = "인원", modifier = Modifier.padding(top = Spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                DallimTextField(
                    value = uiState.minParticipantsInput,
                    onValueChange = onMinParticipantsChange,
                    label = "최소 인원",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                DallimTextField(
                    value = uiState.maxParticipantsInput,
                    onValueChange = onMaxParticipantsChange,
                    label = "최대 인원",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            uiState.participantsError?.let {
                Text(
                    text = it,
                    style = DallimTypography.Caption,
                    color = DallimColors.Error,
                    modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs),
                )
            }

            SectionHeader(title = "초보 환영", modifier = Modifier.padding(top = Spacing.xl))
            DallimFilterChip(
                label = if (uiState.beginnerFriendly) "초보환영 켜짐" else "초보환영 꺼짐",
                selected = uiState.beginnerFriendly,
                onClick = { onBeginnerFriendlyToggle(!uiState.beginnerFriendly) },
            )

            SectionHeader(title = "최소 러닝온도 (선택)", modifier = Modifier.padding(top = Spacing.xl))
            DallimTextField(
                value = uiState.minTemperatureInput,
                onValueChange = onMinTemperatureChange,
                label = "최소 러닝온도",
                placeholder = "예: 37.0",
                keyboardType = KeyboardType.Decimal,
                errorText = uiState.minTemperatureError,
            )
            Text(
                text = "너무 높게 잡으면 참가자가 줄어들 수 있어요.",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xs),
            )

            SectionHeader(title = "참가 성별 조건", modifier = Modifier.padding(top = Spacing.xl))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GenderConditionOptions.forEach { (value, label) ->
                    DallimFilterChip(
                        label = label,
                        selected = uiState.genderCondition == value,
                        onClick = { onGenderConditionSelected(value) },
                    )
                }
            }

            SectionHeader(title = "설명 (선택)", modifier = Modifier.padding(top = Spacing.xl))
            DallimTextField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                label = "설명",
                placeholder = "같이 뛸 사람에게 전할 말을 적어주세요.",
                singleLine = false,
            )

            SectionHeader(title = "집결 장소", modifier = Modifier.padding(top = Spacing.xl))
            MeetingPointPicker(
                selectedRoute = uiState.selectedRoute,
                meetingPoint = uiState.meetingPoint,
                onMeetingPointPicked = onMeetingPointPicked,
            )
            DallimTextField(
                value = uiState.meetingPointDescription,
                onValueChange = onMeetingPointDescriptionChange,
                label = "집결지 상세 설명 (선택)",
                placeholder = "예: 안양천 삼성교 밑 벤치 앞",
                modifier = Modifier.padding(top = Spacing.sm),
            )

            SectionHeader(title = "우천 시 정책", modifier = Modifier.padding(top = Spacing.xl))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RainPolicyOptions.forEach { (value, label) ->
                    DallimFilterChip(
                        label = label,
                        selected = uiState.rainPolicy == value,
                        onClick = { onRainPolicySelected(value) },
                    )
                }
            }

            uiState.errorMessage?.let {
                Text(
                    text = it,
                    style = DallimTypography.Caption,
                    color = DallimColors.Error,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }

            Box(modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.lg)) {
                // 여백만 확보 — 실제 버튼은 아래 고정 영역.
            }
        }

        Box(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md)) {
            DallimPrimaryButton(
                text = if (uiState.isSubmitting) "만드는 중..." else "세션 만들기",
                onClick = onSubmit,
                enabled = uiState.canSubmit,
            )
        }
    }

    if (uiState.isRoutePickerOpen) {
        RoutePickerDialog(
            isLoading = uiState.isLoadingRoutes,
            routes = uiState.routeOptions,
            errorMessage = uiState.routePickerErrorMessage,
            onRouteSelected = onRouteSelected,
            onDismiss = onDismissRoutePicker,
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date
                ?.atStartOfDay(ZoneId.of("UTC"))
                ?.toInstant()
                ?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("확인", color = DallimColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소", color = DallimColors.TextSecondary) }
            },
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = DallimColors.Primary,
                    todayDateBorderColor = DallimColors.Primary,
                    todayContentColor = DallimColors.Primary,
                ),
            )
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.time?.hour ?: LocalTime.now().hour,
            initialMinute = uiState.time?.minute ?: 0,
            is24Hour = false,
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
                Column(modifier = Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "시간 선택", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
                    Box(modifier = Modifier.padding(top = Spacing.md)) {
                        TimePicker(
                            state = timePickerState,
                            colors = TimePickerDefaults.colors(
                                selectorColor = DallimColors.Primary,
                                periodSelectorSelectedContainerColor = DallimColors.PrimaryLight,
                                periodSelectorSelectedContentColor = DallimColors.Primary,
                                timeSelectorSelectedContainerColor = DallimColors.PrimaryLight,
                                timeSelectorSelectedContentColor = DallimColors.Primary,
                            ),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text("취소", color = DallimColors.TextSecondary) }
                        TextButton(onClick = {
                            onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                            showTimePicker = false
                        }) { Text("확인", color = DallimColors.Primary) }
                    }
                }
            }
        }
    }
}

private val GenderConditionOptions = listOf(
    SocialSessionGenderCondition.ANY,
    SocialSessionGenderCondition.SAME_AS_HOST,
    SocialSessionGenderCondition.FEMALE_ONLY,
    SocialSessionGenderCondition.MALE_ONLY,
).map { it to SocialSessionFormat.genderConditionLabel(it) }

private val RainPolicyOptions = listOf(
    SocialSessionRainPolicy.PROCEED,
    SocialSessionRainPolicy.CANCEL,
    SocialSessionRainPolicy.DECIDE_LATER,
).map { it to SocialSessionFormat.rainPolicyLabel(it) }

@Composable
private fun RoutePickerField(route: RouteListItem?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DallimShapes.ButtonCorner)
            .border(1.dp, DallimColors.Border, DallimShapes.ButtonCorner)
            .clickable { onClick() }
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (route != null) {
            RouteThumbnailView(coordinates = route.thumbnailGeoJson.toGeoPoints(), modifier = Modifier.size(40.dp))
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                Text(text = "${route.emoji} ${route.name}", style = DallimTypography.Body, color = DallimColors.TextPrimary)
                Text(
                    text = "${route.distanceKm}km · 약 ${route.estimatedMinutes}분",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                )
            }
        } else {
            Icon(imageVector = DallimIcons.MapPin, contentDescription = null, tint = DallimColors.TextSecondary)
            Text(
                text = "코스를 선택해주세요",
                style = DallimTypography.Body,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}

@Composable
private fun RoutePickerDialog(
    isLoading: Boolean,
    routes: List<RouteListItem>,
    errorMessage: String?,
    onRouteSelected: (RouteListItem) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
            Column(modifier = Modifier.padding(Spacing.lg).height(480.dp)) {
                Text(text = "코스 선택", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
                Box(modifier = Modifier.weight(1f).padding(top = Spacing.md)) {
                    when {
                        isLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(color = DallimColors.Primary) }
                        errorMessage != null -> DallimErrorState(title = "코스를 불러오지 못했어요", description = errorMessage)
                        routes.isEmpty() -> Text(
                            text = "선택할 수 있는 코스가 없어요.",
                            style = DallimTypography.Body,
                            color = DallimColors.TextSecondary,
                        )
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            items(routes, key = { it.routeId }) { route ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRouteSelected(route) }
                                        .padding(vertical = Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RouteThumbnailView(coordinates = route.thumbnailGeoJson.toGeoPoints(), modifier = Modifier.size(48.dp))
                                    Column(modifier = Modifier.padding(start = Spacing.sm)) {
                                        Text(text = "${route.emoji} ${route.name}", style = DallimTypography.Body, color = DallimColors.TextPrimary)
                                        Text(
                                            text = "${route.distanceKm}km · 약 ${route.estimatedMinutes}분",
                                            style = DallimTypography.Caption,
                                            color = DallimColors.TextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("닫기", color = DallimColors.TextSecondary) }
                }
            }
        }
    }
}

/**
 * 집결 장소 지도 핀 — 기존 [NaverRouteMapView]를 그대로 재사용한다(신규 지도 컴포저블 금지, 작업
 * 브리핑 참고): `waypoints`에 지금까지 찍은 점(최대 1개)을 넘기고, `onMapLongClick`으로 새 지점을
 * 받으면 항상 그 하나로 교체한다. NCP Client ID가 없는 빌드에서는 지도 자체가 없으니
 * [RouteThumbnailView] 위경도 롱프레스가 불가능해, 대신 위경도 직접 입력 필드로 대체한다.
 */
@Composable
private fun MeetingPointPicker(
    selectedRoute: RouteListItem?,
    meetingPoint: GeoPoint?,
    onMeetingPointPicked: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (BuildConfig.NAVER_MAP_CLIENT_ID_CONFIGURED) {
            val initialCenter = selectedRoute?.thumbnailGeoJson?.toGeoPoints()?.firstOrNull()
            NaverRouteMapView(
                plannedRoute = emptyList(),
                actualRoute = emptyList(),
                initialCenter = initialCenter,
                waypoints = meetingPoint?.let { listOf(it) } ?: emptyList(),
                onMapLongClick = onMeetingPointPicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(DallimShapes.CardCorner),
            )
            Text(
                text = "지도를 길게 눌러 집결 장소를 찍어주세요.",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                DallimTextField(
                    value = meetingPoint?.lat?.toString().orEmpty(),
                    onValueChange = { value ->
                        val lat = value.toDoubleOrNull() ?: return@DallimTextField
                        onMeetingPointPicked(GeoPoint(lng = meetingPoint?.lng ?: 0.0, lat = lat))
                    },
                    label = "위도",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                DallimTextField(
                    value = meetingPoint?.lng?.toString().orEmpty(),
                    onValueChange = { value ->
                        val lng = value.toDoubleOrNull() ?: return@DallimTextField
                        onMeetingPointPicked(GeoPoint(lng = lng, lat = meetingPoint?.lat ?: 0.0))
                    },
                    label = "경도",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DateTimePickerField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    displayText: String?,
    placeholder: String,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(DallimShapes.MinTapTarget)
            .clip(DallimShapes.ButtonCorner)
            .border(1.dp, if (isError) DallimColors.Error else DallimColors.Border, DallimShapes.ButtonCorner)
            .clickable { onClick() }
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = DallimColors.TextSecondary, modifier = Modifier.size(20.dp))
        Box(modifier = Modifier.width(Spacing.xs))
        Text(
            text = displayText ?: placeholder,
            style = DallimTypography.Body,
            color = if (displayText != null) DallimColors.TextPrimary else DallimColors.TextSecondary,
        )
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun SocialSessionCreateScreenPreview() {
    DallimTheme {
        SocialSessionCreateScreen(
            uiState = SocialSessionCreateUiState(
                selectedRoute = RouteListItem(
                    routeId = "rt_004",
                    name = "고래",
                    emoji = "🐳",
                    distanceKm = 5.1,
                    estimatedMinutes = 36,
                    status = "POPULAR",
                    finisherCount = 148,
                    thumbnailGeoJson = GeoJsonLineString(
                        coordinates = listOf(listOf(127.05, 37.25), listOf(127.052, 37.253)),
                    ),
                ),
                title = "안양천 야간 러닝",
                minParticipantsInput = "4",
                maxParticipantsInput = "6",
            ),
            onBackClick = {},
            onOpenRoutePicker = {},
            onDismissRoutePicker = {},
            onRouteSelected = {},
            onTitleChange = {},
            onDateSelected = {},
            onTimeSelected = {},
            onToggleRunningStyle = {},
            onCustomStyleInputChange = {},
            onAddCustomStyle = {},
            onMinParticipantsChange = {},
            onMaxParticipantsChange = {},
            onBeginnerFriendlyToggle = {},
            onMinTemperatureChange = {},
            onGenderConditionSelected = {},
            onDescriptionChange = {},
            onMeetingPointPicked = {},
            onMeetingPointDescriptionChange = {},
            onRainPolicySelected = {},
            onSubmit = {},
        )
    }
}
