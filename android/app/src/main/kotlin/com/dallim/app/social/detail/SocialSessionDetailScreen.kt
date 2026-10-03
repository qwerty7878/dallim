package com.dallim.app.social.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.social.SocialAvatarBadge
import com.dallim.app.social.SocialSessionFormat
import com.dallim.network.common.GeoJsonLineString
import com.dallim.network.social.SocialSessionApplicantStatus
import com.dallim.network.social.SocialSessionDetailResponseBody
import com.dallim.network.social.SocialSessionParticipantItem
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTopBar
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.DallimTextField
import com.dallim.ui.components.GeoPoint
import com.dallim.ui.components.RouteThumbnailView
import com.dallim.ui.components.SectionHeader
import com.dallim.ui.components.SocialSessionStatusBadge
import com.dallim.ui.components.socialSessionBadgeState
import com.dallim.ui.components.DallimTag
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-32 세션 상세 (docs/달림_화면별_상세기획서_v1.3.md PART 3-D, docs/02-api-spec.md 17.4).
 * [공유]/[신고] 버튼은 이번 1단계에서 만들지 않는다(신고는 2단계에서 백엔드가 만든다 — 작업
 * 브리핑 참고). S-33(참가 신청)은 별도 화면이 아니라 이 화면 위의 다이얼로그로 존재한다.
 */
@Composable
fun SocialSessionDetailRoute(
    onBackClick: () -> Unit,
    onApplicantsClick: (sessionId: String) -> Unit,
    onChatClick: (sessionId: String) -> Unit,
    onCheckinClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SocialSessionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SocialSessionDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onApplicantsClick = {
            val detail = (uiState as? SocialSessionDetailUiState.Success)?.detail ?: return@SocialSessionDetailScreen
            onApplicantsClick(detail.sessionId)
        },
        onChatClick = {
            val detail = (uiState as? SocialSessionDetailUiState.Success)?.detail ?: return@SocialSessionDetailScreen
            onChatClick(detail.sessionId)
        },
        onCheckinClick = {
            val detail = (uiState as? SocialSessionDetailUiState.Success)?.detail ?: return@SocialSessionDetailScreen
            onCheckinClick(detail.sessionId)
        },
        onApplyClick = viewModel::onApplyClick,
        onApplyDialogDismiss = viewModel::onApplyDialogDismiss,
        onApplyMessageChange = viewModel::onApplyMessageChange,
        onApplySubmit = viewModel::onApplySubmit,
        onCancelApplyClick = viewModel::onCancelApplyClick,
        modifier = modifier,
    )
}

@Composable
private fun SocialSessionDetailScreen(
    uiState: SocialSessionDetailUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onApplicantsClick: () -> Unit,
    onChatClick: () -> Unit,
    onCheckinClick: () -> Unit,
    onApplyClick: () -> Unit,
    onApplyDialogDismiss: () -> Unit,
    onApplyMessageChange: (String) -> Unit,
    onApplySubmit: () -> Unit,
    onCancelApplyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        DallimTopBar(title = "세션 상세", onBackClick = onBackClick)

        when (uiState) {
            SocialSessionDetailUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is SocialSessionDetailUiState.Error -> DallimErrorState(
                title = "세션 정보를 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is SocialSessionDetailUiState.Success -> SocialSessionDetailContent(
                state = uiState,
                onApplicantsClick = onApplicantsClick,
                onChatClick = onChatClick,
                onCheckinClick = onCheckinClick,
                onApplyClick = onApplyClick,
                onCancelApplyClick = onCancelApplyClick,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (uiState is SocialSessionDetailUiState.Success && uiState.isApplyDialogOpen) {
        ApplySocialSessionDialog(
            message = uiState.applyMessage,
            isSubmitting = uiState.isApplying,
            errorMessage = uiState.applyErrorMessage,
            onMessageChange = onApplyMessageChange,
            onSubmit = onApplySubmit,
            onDismiss = onApplyDialogDismiss,
        )
    }
}

/** 이 화면에서 취할 수 있는 단일 액션 — 화면당 강조 버튼은 하나(04-ui-guide.md §2). */
private sealed interface SocialSessionDetailAction {
    data object ManageApplicants : SocialSessionDetailAction
    data object Apply : SocialSessionDetailAction
    /** `applicationOpen == false`(2026-09-16 신규, 행사 3일 전 마감) — 아직 신청한 적 없는데
     * 신청 창이 닫힌 경우. "신청하기" 버튼을 비활성화 상태로 바꿔 보여준다. */
    data object ApplicationClosed : SocialSessionDetailAction
    data object CancelApply : SocialSessionDetailAction
    data class StatusOnly(val label: String, val color: androidx.compose.ui.graphics.Color) : SocialSessionDetailAction
    data object None : SocialSessionDetailAction
}

private fun resolveAction(detail: SocialSessionDetailResponseBody): SocialSessionDetailAction {
    if (detail.isHost) return SocialSessionDetailAction.ManageApplicants
    return when (detail.myApplicationStatus) {
        null -> if (detail.applicationOpen) SocialSessionDetailAction.Apply else SocialSessionDetailAction.ApplicationClosed
        SocialSessionApplicantStatus.PENDING -> SocialSessionDetailAction.CancelApply
        SocialSessionApplicantStatus.APPROVED ->
            SocialSessionDetailAction.StatusOnly("참가가 확정됐어요", DallimColors.Success)
        SocialSessionApplicantStatus.CANCELLED ->
            SocialSessionDetailAction.StatusOnly("신청을 취소했어요", DallimColors.TextSecondary)
        SocialSessionApplicantStatus.EXPIRED ->
            SocialSessionDetailAction.StatusOnly("신청이 만료됐어요", DallimColors.TextSecondary)
        else -> SocialSessionDetailAction.None
    }
}

@Composable
private fun SocialSessionDetailContent(
    state: SocialSessionDetailUiState.Success,
    onApplicantsClick: () -> Unit,
    onChatClick: () -> Unit,
    onCheckinClick: () -> Unit,
    onApplyClick: () -> Unit,
    onCancelApplyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    val action = resolveAction(detail)
    // S-35/S-36/S-37 진입 가드 — 호스트이거나 APPROVED 참가자만(작업 브리핑 지시).
    val canAccessSessionTools = detail.isHost || detail.myApplicationStatus == SocialSessionApplicantStatus.APPROVED

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Spacing.ScreenHorizontal,
                end = Spacing.ScreenHorizontal,
                top = Spacing.sm,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                DallimCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = detail.title,
                            style = DallimTypography.Title1,
                            color = DallimColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        SocialSessionStatusBadge(state = socialSessionBadgeState(detail.status))
                    }

                    Row(modifier = Modifier.padding(top = Spacing.md)) {
                        RouteThumbnailView(
                            coordinates = detail.routeThumbnailGeoJson?.toGeoPoints() ?: emptyList(),
                            modifier = Modifier.size(56.dp),
                        )
                        Column(modifier = Modifier.padding(start = Spacing.sm)) {
                            Text(text = detail.routeName, style = DallimTypography.Body, color = DallimColors.TextPrimary)
                            val distance = detail.routeDistanceKm
                            val minutes = detail.routeEstimatedMinutes
                            if (distance != null && minutes != null) {
                                Text(
                                    text = "${"%.2f".format(distance)}km · 약 ${minutes}분",
                                    style = DallimTypography.Caption,
                                    color = DallimColors.TextSecondary,
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md), color = DallimColors.Border)

                    DetailRow(label = "일시", value = SocialSessionFormat.displayDateTime(detail.scheduledAt))
                    DetailRow(
                        label = "집결지",
                        value = detail.meetingPointDetail ?: detail.meetingPointHint,
                    )
                    DetailRow(label = "인원", value = "승인됨 ${detail.approvedCount}/${detail.maxParticipants}명 (최소 ${detail.minParticipants}명)")
                    DetailRow(label = "우천 시", value = SocialSessionFormat.rainPolicyLabel(detail.rainPolicy))
                    if (detail.genderCondition != "ANY") {
                        DetailRow(label = "참가 조건", value = SocialSessionFormat.genderConditionLabel(detail.genderCondition))
                    }
                    val minTemperature = detail.minRunningTemperature
                    if (minTemperature != null) {
                        DetailRow(
                            label = "최소 러닝온도",
                            value = SocialSessionFormat.displayTemperature(minTemperature),
                        )
                    }

                    if (detail.beginnerFriendly || detail.runningStyles.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.padding(top = Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            if (detail.beginnerFriendly) {
                                item { StyleTag(text = "초보환영") }
                            }
                            items(detail.runningStyles) { style -> StyleTag(text = style) }
                        }
                    }

                    val description = detail.description
                    if (!description.isNullOrBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md), color = DallimColors.Border)
                        Text(text = description, style = DallimTypography.Body, color = DallimColors.TextPrimary)
                    }
                }
            }

            if (canAccessSessionTools) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        DallimSecondaryButton(text = "채팅", onClick = onChatClick, modifier = Modifier.weight(1f))
                        DallimSecondaryButton(text = "체크인 · Ready Check", onClick = onCheckinClick, modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionHeader(title = "호스트")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SocialAvatarBadge(avatarId = detail.hostAvatarId, size = 48.dp)
                    Column(modifier = Modifier.padding(start = Spacing.md)) {
                        Text(text = detail.hostNickname, style = DallimTypography.Body, color = DallimColors.TextPrimary)
                        Text(
                            text = SocialSessionFormat.displayTemperature(detail.hostRunningTemperature),
                            style = DallimTypography.Caption,
                            color = DallimColors.TextSecondary,
                        )
                    }
                }
            }

            item {
                SectionHeader(title = "참가자 (${detail.participants.size})")
            }

            if (detail.participants.isEmpty()) {
                item {
                    Text(
                        text = "아직 승인된 참가자가 없어요.",
                        style = DallimTypography.Caption,
                        color = DallimColors.TextSecondary,
                    )
                }
            }

            items(detail.participants, key = { it.userId }) { participant -> ParticipantRow(participant) }

            if (state.actionErrorMessage != null) {
                item {
                    Text(
                        text = state.actionErrorMessage,
                        style = DallimTypography.Caption,
                        color = DallimColors.Error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }

        Box(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md)) {
            when (action) {
                SocialSessionDetailAction.ManageApplicants -> DallimPrimaryButton(
                    text = "신청자 관리",
                    onClick = onApplicantsClick,
                )
                SocialSessionDetailAction.Apply -> DallimPrimaryButton(
                    text = "참가 신청",
                    onClick = onApplyClick,
                )
                SocialSessionDetailAction.ApplicationClosed -> Column {
                    Text(
                        text = "마감 3일 전까지만 신청할 수 있어요.",
                        style = DallimTypography.Caption,
                        color = DallimColors.TextSecondary,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                    DallimPrimaryButton(
                        text = "신청 마감",
                        onClick = {},
                        enabled = false,
                    )
                }
                SocialSessionDetailAction.CancelApply -> Column {
                    Text(
                        text = "호스트가 확인 중이에요",
                        style = DallimTypography.Caption,
                        color = DallimColors.TextSecondary,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                    DallimSecondaryButton(
                        text = if (state.isActionInProgress) "취소하는 중..." else "신청 취소",
                        onClick = onCancelApplyClick,
                        enabled = !state.isActionInProgress,
                    )
                }
                is SocialSessionDetailAction.StatusOnly -> Text(
                    text = action.label,
                    style = DallimTypography.Body,
                    color = action.color,
                )
                SocialSessionDetailAction.None -> Unit
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        Text(
            text = label,
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.width(88.dp),
        )
        Text(text = value, style = DallimTypography.Body, color = DallimColors.TextPrimary)
    }
}

@Composable
private fun StyleTag(text: String, modifier: Modifier = Modifier) {
    DallimTag(text = text, modifier = modifier)
}

@Composable
private fun ParticipantRow(participant: SocialSessionParticipantItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialAvatarBadge(avatarId = participant.avatarId, size = 40.dp)
        Text(
            text = participant.nickname,
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            modifier = Modifier.padding(start = Spacing.md),
        )
    }
}

/** S-33 참가 신청 — 별도 화면이 아니라 이 다이얼로그 하나로 충분하다(작업 브리핑 지시: "신청 후
 * '호스트가 확인 중이에요' 상태 표시는 S-32 상세 화면의 `myApplicationStatus`로 이미 커버됨"). */
@Composable
private fun ApplySocialSessionDialog(
    message: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onMessageChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(text = "참가 신청", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
                Text(
                    text = "호스트에게 보낼 한 줄 메시지를 남겨보세요 (선택).",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                )
                DallimTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    label = "메시지",
                    placeholder = "예: 초보인데 같이 뛰어도 될까요?",
                    singleLine = false,
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = DallimTypography.Caption,
                        color = DallimColors.Error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DallimTextButton(text = "닫기", onClick = onDismiss, enabled = !isSubmitting)
                    DallimTextButton(
                        text = if (isSubmitting) "신청하는 중..." else "신청하기",
                        onClick = onSubmit,
                        enabled = !isSubmitting,
                    )
                }
            }
        }
    }
}

private fun GeoJsonLineString.toGeoPoints(): List<GeoPoint> =
    toLngLatPairs().map { (lng, lat) -> GeoPoint(lng = lng, lat = lat) }

private fun previewDetail(
    isHost: Boolean,
    myApplicationStatus: String? = null,
    status: String = "RECRUITING",
    applicationOpen: Boolean = true,
) = SocialSessionDetailResponseBody(
    sessionId = "ss_001",
    hostUserId = "usr_1",
    hostNickname = "달림이",
    hostAvatarId = "avatar_02",
    hostRunningTemperature = 37.2,
    routeId = "rt_004",
    routeName = "고래",
    routeThumbnailGeoJson = GeoJsonLineString(coordinates = listOf(listOf(127.05, 37.25), listOf(127.052, 37.253))),
    routeDistanceKm = 5.1,
    routeEstimatedMinutes = 36,
    title = "안양천 야간 러닝",
    scheduledAt = "2026-09-20T21:00:00Z",
    minParticipants = 4,
    maxParticipants = 6,
    approvedCount = 3,
    runningStyles = listOf("대화하면서", "초보환영조합"),
    beginnerFriendly = true,
    minRunningTemperature = null,
    genderCondition = "ANY",
    description = "천천히 대화하면서 뛰어요",
    meetingPointLat = 37.5123,
    meetingPointLng = 126.9234,
    meetingPointDetail = if (isHost) "안양천 삼성교 밑 벤치 앞" else null,
    meetingPointHint = "안양천 인근",
    rainPolicy = "DECIDE_LATER",
    status = status,
    isHost = isHost,
    myApplicationStatus = myApplicationStatus,
    participants = listOf(
        SocialSessionParticipantItem(userId = "usr_2", nickname = "러너B", avatarId = "avatar_01"),
    ),
    applicationOpen = applicationOpen,
)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionDetailScreenApplyPreview() {
    DallimTheme {
        SocialSessionDetailScreen(
            uiState = SocialSessionDetailUiState.Success(detail = previewDetail(isHost = false)),
            onBackClick = {},
            onRetryClick = {},
            onApplicantsClick = {},
            onChatClick = {},
            onCheckinClick = {},
            onApplyClick = {},
            onApplyDialogDismiss = {},
            onApplyMessageChange = {},
            onApplySubmit = {},
            onCancelApplyClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionDetailScreenPendingPreview() {
    DallimTheme {
        SocialSessionDetailScreen(
            uiState = SocialSessionDetailUiState.Success(
                detail = previewDetail(isHost = false, myApplicationStatus = "PENDING"),
            ),
            onBackClick = {},
            onRetryClick = {},
            onApplicantsClick = {},
            onChatClick = {},
            onCheckinClick = {},
            onApplyClick = {},
            onApplyDialogDismiss = {},
            onApplyMessageChange = {},
            onApplySubmit = {},
            onCancelApplyClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionDetailScreenApplicationClosedPreview() {
    DallimTheme {
        SocialSessionDetailScreen(
            uiState = SocialSessionDetailUiState.Success(
                detail = previewDetail(isHost = false, applicationOpen = false, status = "CLOSED"),
            ),
            onBackClick = {},
            onRetryClick = {},
            onApplicantsClick = {},
            onChatClick = {},
            onCheckinClick = {},
            onApplyClick = {},
            onApplyDialogDismiss = {},
            onApplyMessageChange = {},
            onApplySubmit = {},
            onCancelApplyClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionDetailScreenHostPreview() {
    DallimTheme {
        SocialSessionDetailScreen(
            uiState = SocialSessionDetailUiState.Success(detail = previewDetail(isHost = true)),
            onBackClick = {},
            onRetryClick = {},
            onApplicantsClick = {},
            onChatClick = {},
            onCheckinClick = {},
            onApplyClick = {},
            onApplyDialogDismiss = {},
            onApplyMessageChange = {},
            onApplySubmit = {},
            onCancelApplyClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionDetailScreenApplyDialogPreview() {
    DallimTheme {
        SocialSessionDetailScreen(
            uiState = SocialSessionDetailUiState.Success(
                detail = previewDetail(isHost = false),
                isApplyDialogOpen = true,
            ),
            onBackClick = {},
            onRetryClick = {},
            onApplicantsClick = {},
            onChatClick = {},
            onCheckinClick = {},
            onApplyClick = {},
            onApplyDialogDismiss = {},
            onApplyMessageChange = {},
            onApplySubmit = {},
            onCancelApplyClick = {},
        )
    }
}
