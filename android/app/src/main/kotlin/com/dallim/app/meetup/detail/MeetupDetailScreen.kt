package com.dallim.app.meetup.detail

import com.dallim.ui.icons.DallimIcons
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.meetup.MeetupFormat
import com.dallim.network.meetup.MeetupDetailResponseBody
import com.dallim.network.meetup.MeetupParticipantItem
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTopBar
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.components.DallimSecondaryButton
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.MeetupStatusBadge
import com.dallim.ui.components.SectionHeader
import com.dallim.ui.components.meetupBadgeState
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-49 모집 상세 — 참가자 목록, 참가/참가취소, 작성자는 모집취소 (docs/01-feature-spec.md §1.8,
 * docs/02-api-spec.md 14.4). 성공적으로 모집을 취소하면 [onCancelled]를 호출해 NavHost가 S-47로
 * 돌아가면서 목록을 새로고침하도록 위임한다(S-48의 `onCreated` 패턴과 동일). 단순 뒤로가기
 * ([onBackClick])에서도 참가/나가기로 상태가 바뀌었을 수 있으므로 목록을 새로고침해야 한다 — 그
 * 판단은 NavHost가 담당한다(둘 다 항상 새로고침 플래그를 심는다).
 */
@Composable
fun MeetupDetailRoute(
    onBackClick: () -> Unit,
    onCancelled: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeetupDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                MeetupDetailNavigationEvent.Cancelled -> onCancelled()
            }
        }
    }

    MeetupDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onJoinClick = viewModel::onJoinClick,
        onLeaveClick = viewModel::onLeaveClick,
        onCancelClick = viewModel::onCancelClick,
        onCancelConfirm = viewModel::onCancelConfirm,
        onCancelDismiss = viewModel::onCancelDismiss,
        modifier = modifier,
    )
}

@Composable
private fun MeetupDetailScreen(
    uiState: MeetupDetailUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onJoinClick: () -> Unit,
    onLeaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    onCancelConfirm: () -> Unit,
    onCancelDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        DallimTopBar(title = "모집 상세", onBackClick = onBackClick)

        when (uiState) {
            MeetupDetailUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is MeetupDetailUiState.Error -> DallimErrorState(
                title = "모집 정보를 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is MeetupDetailUiState.Success -> MeetupDetailContent(
                state = uiState,
                onJoinClick = onJoinClick,
                onLeaveClick = onLeaveClick,
                onCancelClick = onCancelClick,
                onCancelConfirm = onCancelConfirm,
                onCancelDismiss = onCancelDismiss,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 이 화면에서 취할 수 있는 단일 액션 — 화면당 강조 버튼은 하나(04-ui-guide.md §2). */
private sealed interface MeetupDetailAction {
    data object HostCancel : MeetupDetailAction
    data object Leave : MeetupDetailAction
    data object Join : MeetupDetailAction
    data object None : MeetupDetailAction
}

/**
 * 14.1/1.8.2: "마감"·"종료"는 `isFull`/`isPast`를 서버가 미리 계산해 내려주고, "취소됨"은
 * `status == CANCELLED`. 정원이 찬 미래 모집이라도 호스트는 여전히 취소할 수 있어야 하고, 이미
 * 참가한 사람은 여전히 나갈 수 있어야 한다 — `isFull`은 "새로 참가하기"만 막는다. `isPast`/
 * `CANCELLED`("종료된" 모집)는 더 이상 어떤 액션도 의미가 없으므로 호스트/참가자 모두에게
 * 버튼을 숨긴다.
 */
private fun resolveAction(detail: MeetupDetailResponseBody): MeetupDetailAction {
    val ended = detail.isPast || detail.isCancelled()
    return when {
        detail.isHost -> if (ended) MeetupDetailAction.None else MeetupDetailAction.HostCancel
        detail.isJoined -> if (ended) MeetupDetailAction.None else MeetupDetailAction.Leave
        ended || detail.isFull -> MeetupDetailAction.None
        else -> MeetupDetailAction.Join
    }
}

@Composable
private fun MeetupDetailContent(
    state: MeetupDetailUiState.Success,
    onJoinClick: () -> Unit,
    onLeaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    onCancelConfirm: () -> Unit,
    onCancelDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    val action = resolveAction(detail)

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
                            text = detail.routeName,
                            style = DallimTypography.Title1,
                            color = DallimColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        MeetupStatusBadge(
                            state = meetupBadgeState(detail.status, detail.isFull, detail.isPast),
                        )
                    }

                    Text(
                        text = "호스트 ${detail.hostNickname}",
                        style = DallimTypography.Body,
                        color = DallimColors.TextSecondary,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                    Text(
                        text = MeetupFormat.displayDateTime(detail.scheduledAt),
                        style = DallimTypography.Body,
                        color = DallimColors.TextPrimary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Text(
                        text = "${detail.participants.size}/${detail.maxParticipants}명 참가",
                        style = DallimTypography.Body,
                        color = DallimColors.TextPrimary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )

                    val description = detail.description
                    if (!description.isNullOrBlank()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Spacing.md),
                            color = DallimColors.Border,
                        )
                        Text(text = description, style = DallimTypography.Body, color = DallimColors.TextPrimary)
                    }
                }
            }

            item {
                SectionHeader(title = "참가자 (${detail.participants.size})")
            }

            items(detail.participants, key = { it.userId }) { participant ->
                ParticipantRow(participant = participant)
            }

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

        if (action != MeetupDetailAction.None) {
            Box(modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md)) {
                when (action) {
                    MeetupDetailAction.HostCancel -> DallimSecondaryButton(
                        text = if (state.isActionInProgress) "취소하는 중..." else "모집 취소",
                        onClick = onCancelClick,
                        enabled = !state.isActionInProgress,
                    )
                    MeetupDetailAction.Leave -> DallimSecondaryButton(
                        text = if (state.isActionInProgress) "나가는 중..." else "참가 취소",
                        onClick = onLeaveClick,
                        enabled = !state.isActionInProgress,
                    )
                    MeetupDetailAction.Join -> DallimPrimaryButton(
                        text = if (state.isActionInProgress) "참가하는 중..." else "참가하기",
                        onClick = onJoinClick,
                        enabled = !state.isActionInProgress,
                    )
                    MeetupDetailAction.None -> Unit
                }
            }
        }
    }

    if (state.showCancelConfirm) {
        MeetupCancelConfirmDialog(onConfirm = onCancelConfirm, onDismiss = onCancelDismiss)
    }
}

@Composable
private fun ParticipantRow(participant: MeetupParticipantItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DallimColors.PrimaryLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = DallimIcons.User, contentDescription = null, tint = DallimColors.Primary)
        }
        Text(
            text = participant.nickname,
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.md),
        )
        if (participant.isHost) {
            Text(
                text = "호스트",
                style = DallimTypography.Caption,
                color = DallimColors.Primary,
                modifier = Modifier
                    .clip(DallimShapes.ButtonCorner)
                    .background(DallimColors.PrimaryLight)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    }
}

/** 모집 취소는 되돌릴 수 없는 액션이라 확인 절차를 거친다(작업 지시서 — SPEC엔 별도 명시 없음). */
@Composable
private fun MeetupCancelConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(text = "모집을 취소할까요?", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
                Text(
                    text = "취소하면 되돌릴 수 없어요. 참가자들에게는 '취소됨'으로 계속 표시돼요.",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DallimTextButton(text = "닫기", onClick = onDismiss)
                    DallimTextButton(text = "모집 취소", onClick = onConfirm)
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MeetupDetailScreenJoinPreview() {
    DallimTheme {
        MeetupDetailScreen(
            uiState = MeetupDetailUiState.Success(detail = previewDetail(isHost = false, isJoined = false)),
            onBackClick = {},
            onRetryClick = {},
            onJoinClick = {},
            onLeaveClick = {},
            onCancelClick = {},
            onCancelConfirm = {},
            onCancelDismiss = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MeetupDetailScreenJoinedPreview() {
    DallimTheme {
        MeetupDetailScreen(
            uiState = MeetupDetailUiState.Success(detail = previewDetail(isHost = false, isJoined = true)),
            onBackClick = {},
            onRetryClick = {},
            onJoinClick = {},
            onLeaveClick = {},
            onCancelClick = {},
            onCancelConfirm = {},
            onCancelDismiss = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MeetupDetailScreenHostPreview() {
    DallimTheme {
        MeetupDetailScreen(
            uiState = MeetupDetailUiState.Success(detail = previewDetail(isHost = true, isJoined = true)),
            onBackClick = {},
            onRetryClick = {},
            onJoinClick = {},
            onLeaveClick = {},
            onCancelClick = {},
            onCancelConfirm = {},
            onCancelDismiss = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MeetupDetailScreenFullPreview() {
    DallimTheme {
        MeetupDetailScreen(
            uiState = MeetupDetailUiState.Success(
                detail = previewDetail(isHost = false, isJoined = false, isFull = true),
            ),
            onBackClick = {},
            onRetryClick = {},
            onJoinClick = {},
            onLeaveClick = {},
            onCancelClick = {},
            onCancelConfirm = {},
            onCancelDismiss = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MeetupDetailScreenCancelledPreview() {
    DallimTheme {
        MeetupDetailScreen(
            uiState = MeetupDetailUiState.Success(
                detail = previewDetail(isHost = true, isJoined = true, status = "CANCELLED"),
            ),
            onBackClick = {},
            onRetryClick = {},
            onJoinClick = {},
            onLeaveClick = {},
            onCancelClick = {},
            onCancelConfirm = {},
            onCancelDismiss = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MeetupDetailScreenCancelConfirmPreview() {
    DallimTheme {
        MeetupDetailScreen(
            uiState = MeetupDetailUiState.Success(
                detail = previewDetail(isHost = true, isJoined = true),
                showCancelConfirm = true,
            ),
            onBackClick = {},
            onRetryClick = {},
            onJoinClick = {},
            onLeaveClick = {},
            onCancelClick = {},
            onCancelConfirm = {},
            onCancelDismiss = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun MeetupDetailScreenErrorPreview() {
    DallimTheme {
        MeetupDetailScreen(
            uiState = MeetupDetailUiState.Error("네트워크 연결을 확인해주세요."),
            onBackClick = {},
            onRetryClick = {},
            onJoinClick = {},
            onLeaveClick = {},
            onCancelClick = {},
            onCancelConfirm = {},
            onCancelDismiss = {},
        )
    }
}

private fun previewDetail(
    isHost: Boolean,
    isJoined: Boolean,
    isFull: Boolean = false,
    status: String = "OPEN",
) = MeetupDetailResponseBody(
    meetupId = "mt_001",
    routeId = "rt_002",
    routeName = "물고기",
    hostUserId = "usr_1",
    hostNickname = "달림이",
    scheduledAt = "2026-09-13T22:00:00Z",
    maxParticipants = 6,
    description = "천천히 페이스로 완주 목표예요. 준비물은 따로 없어요!",
    status = status,
    isFull = isFull,
    isPast = false,
    isHost = isHost,
    isJoined = isJoined,
    participants = listOf(
        MeetupParticipantItem(userId = "usr_1", nickname = "달림이", isHost = true),
        MeetupParticipantItem(userId = "usr_2", nickname = "러너B", isHost = false),
    ),
)
