package com.dallim.app.social.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.social.SocialAvatarBadge
import com.dallim.app.social.SocialSessionFormat
import com.dallim.network.social.ChatMessageItem
import com.dallim.network.social.SocialChatMessageType
import com.dallim.network.social.SocialQuickMessages
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimFilterChip
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimTextButton
import com.dallim.ui.components.DallimTextField
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-35 팀 채팅 (docs/02-api-spec.md 17.11/17.12) — Quick Message 4종 고정 문구, 호스트 공지
 * 자동 강조, 메시지/세션 신고. [com.dallim.app.social.detail.SocialSessionDetailScreen]에서
 * 호스트 또는 `APPROVED` 참가자만 진입 버튼을 본다.
 */
@Composable
fun SocialSessionChatRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SocialSessionChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SocialSessionChatScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onDraftTextChange = viewModel::onDraftTextChange,
        onSendClick = viewModel::onSendClick,
        onQuickMessageClick = viewModel::onQuickMessageClick,
        onCancelReasonChange = viewModel::onCancelReasonChange,
        onCancelQuickMessageDismiss = viewModel::onCancelQuickMessageDismiss,
        onCancelQuickMessageConfirm = viewModel::onCancelQuickMessageConfirm,
        onMessageLongPress = viewModel::onMessageLongPress,
        onActionMenuDismiss = viewModel::onActionMenuDismiss,
        onReportFromActionMenuClick = viewModel::onReportFromActionMenuClick,
        onBlockUserFromActionMenuClick = viewModel::onBlockUserFromActionMenuClick,
        onSessionReportClick = viewModel::onSessionReportClick,
        onReportDialogDismiss = viewModel::onReportDialogDismiss,
        onReportReasonChange = viewModel::onReportReasonChange,
        onReportSubmit = viewModel::onReportSubmit,
        onToastMessageShown = viewModel::onToastMessageShown,
        modifier = modifier,
    )
}

@Composable
private fun SocialSessionChatScreen(
    uiState: SocialSessionChatUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDraftTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onQuickMessageClick: (String) -> Unit,
    onCancelReasonChange: (String) -> Unit,
    onCancelQuickMessageDismiss: () -> Unit,
    onCancelQuickMessageConfirm: () -> Unit,
    onMessageLongPress: (String) -> Unit,
    onActionMenuDismiss: () -> Unit,
    onReportFromActionMenuClick: () -> Unit,
    onBlockUserFromActionMenuClick: () -> Unit,
    onSessionReportClick: () -> Unit,
    onReportDialogDismiss: () -> Unit,
    onReportReasonChange: (String) -> Unit,
    onReportSubmit: () -> Unit,
    onToastMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val toastMessage = (uiState as? SocialSessionChatUiState.Success)?.toastMessage

    LaunchedEffect(toastMessage) {
        val message = toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onToastMessageShown()
    }

    Scaffold(
        modifier = modifier,
        containerColor = DallimColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DallimColors.Background)
                .padding(innerPadding)
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = DallimColors.TextPrimary,
                    )
                }
                Text(
                    text = "팀 채팅",
                    style = DallimTypography.Title1,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (uiState is SocialSessionChatUiState.Success) {
                    IconButton(onClick = onSessionReportClick) {
                        Icon(imageVector = Icons.Filled.Flag, contentDescription = "세션 신고", tint = DallimColors.TextSecondary)
                    }
                }
            }

            when (uiState) {
                SocialSessionChatUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
                is SocialSessionChatUiState.Error -> DallimErrorState(
                    title = "채팅을 불러오지 못했어요",
                    description = uiState.message,
                    onRetry = if (uiState.isAccessExpired) null else onRetryClick,
                    modifier = Modifier.weight(1f),
                )
                is SocialSessionChatUiState.Success -> ChatContent(
                    state = uiState,
                    onDraftTextChange = onDraftTextChange,
                    onSendClick = onSendClick,
                    onQuickMessageClick = onQuickMessageClick,
                    onMessageLongPress = onMessageLongPress,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (uiState is SocialSessionChatUiState.Success && uiState.pendingCancelQuickMessage) {
        CancelQuickMessageDialog(
            reason = uiState.cancelReasonDraft,
            onReasonChange = onCancelReasonChange,
            onDismiss = onCancelQuickMessageDismiss,
            onConfirm = onCancelQuickMessageConfirm,
        )
    }

    if (uiState is SocialSessionChatUiState.Success && uiState.isReportDialogOpen) {
        ReportDialog(
            isMessageReport = uiState.reportTargetMessageId != null,
            reason = uiState.reportReasonDraft,
            isSubmitting = uiState.isSubmittingReport,
            onReasonChange = onReportReasonChange,
            onDismiss = onReportDialogDismiss,
            onSubmit = onReportSubmit,
        )
    }

    val actionMenuTarget = (uiState as? SocialSessionChatUiState.Success)
        ?.let { state -> state.messages.firstOrNull { it.id == state.actionMenuTargetMessageId } }
    if (uiState is SocialSessionChatUiState.Success && actionMenuTarget != null) {
        // 2026-09-16 신규(구현 7) — 자기 자신 메시지에는 "이 사용자 차단" 항목을 숨긴다.
        val canBlock = actionMenuTarget.senderUserId != null && actionMenuTarget.senderUserId != uiState.currentUserId
        MessageActionDialog(
            canBlock = canBlock,
            onReportClick = onReportFromActionMenuClick,
            onBlockClick = onBlockUserFromActionMenuClick,
            onDismiss = onActionMenuDismiss,
        )
    }
}

@Composable
private fun ChatContent(
    state: SocialSessionChatUiState.Success,
    onDraftTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onQuickMessageClick: (String) -> Unit,
    onMessageLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MeetingInfoCard(meetingPointText = state.meetingPointText, announcement = state.latestAnnouncement)

        if (state.messages.isEmpty()) {
            Text(
                text = "아직 대화가 없어요. 첫 메시지를 남겨보세요.",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(Spacing.xl),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm, alignment = Alignment.Bottom),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatMessageRow(
                        message = message,
                        isBlocked = message.senderUserId != null && message.senderUserId in state.blockedUserIds,
                        onLongPress = { onMessageLongPress(message.id) },
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(SocialQuickMessages.ALL) { text ->
                DallimFilterChip(label = text, selected = false, onClick = { onQuickMessageClick(text) })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DallimTextField(
                value = state.draftText,
                onValueChange = onDraftTextChange,
                label = "메시지",
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSendClick) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "전송", tint = DallimColors.Primary)
            }
        }
    }
}

/**
 * 상단 고정(스크롤 안 되는) 카드 — 집결지 정보 + 가장 최근 호스트 공지(2026-09-16 신규, 사용자
 * 지시). 둘 다 없으면 아무것도 그리지 않는다(과설계 금지 — 새 "공지 작성" 폼 없음, 기존 채팅
 * 입력창으로 TEXT를 보내는 것 자체가 서버에서 자동으로 공지 승격됨).
 */
@Composable
private fun MeetingInfoCard(meetingPointText: String?, announcement: ChatMessageItem?, modifier: Modifier = Modifier) {
    if (meetingPointText == null && announcement == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DallimColors.PrimaryLight)
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.sm),
    ) {
        if (meetingPointText != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = DallimColors.Primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = meetingPointText,
                    style = DallimTypography.Caption,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
        if (announcement != null) {
            Row(
                modifier = Modifier.padding(top = if (meetingPointText != null) Spacing.xs else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = DallimColors.Primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = announcement.body,
                    style = DallimTypography.Caption,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessageItem,
    isBlocked: Boolean,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 2026-09-16 신규(구현 7) — 차단한 발신자의 메시지는 접어서 보여준다. 순수 클라이언트
    // 필터라 서버 응답은 그대로 두고 여기서만 렌더링을 바꾼다. 탭하면 펼쳐서 원래 내용을 볼 수
    // 있다(완전히 숨기지 않음 — 신고/차단 취소 등 맥락 파악이 필요할 수 있어서).
    var expanded by remember(message.id) { mutableStateOf(false) }
    if (isBlocked && !expanded) {
        Text(
            text = "차단한 사용자의 메시지입니다. 탭하면 볼 수 있어요.",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = Spacing.xs),
        )
        return
    }

    when (message.type) {
        SocialChatMessageType.SYSTEM -> Text(
            text = message.body,
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        )
        SocialChatMessageType.HOST_ANNOUNCEMENT -> Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(DallimShapes.CardCorner)
                .background(DallimColors.PrimaryLight)
                .longPressable(onLongPress)
                .padding(Spacing.sm),
        ) {
            Text(text = "공지 · ${message.senderNickname.orEmpty()}", style = DallimTypography.Caption, color = DallimColors.Primary)
            Text(
                text = message.body,
                style = DallimTypography.Body,
                color = DallimColors.TextPrimary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        else -> Row(modifier = modifier.fillMaxWidth().longPressable(onLongPress), verticalAlignment = Alignment.Top) {
            SocialAvatarBadge(avatarId = message.senderAvatarId, size = 32.dp)
            Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = message.senderNickname.orEmpty(), style = DallimTypography.Caption, color = DallimColors.TextSecondary)
                    Text(
                        text = SocialSessionFormat.displayAppliedAt(message.createdAt),
                        style = DallimTypography.Caption,
                        color = DallimColors.TextSecondary,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
                val bubbleColor = if (message.type == SocialChatMessageType.QUICK_MESSAGE) {
                    DallimColors.PrimaryLight
                } else {
                    DallimColors.Surface
                }
                Text(
                    text = message.body,
                    style = DallimTypography.Body,
                    color = DallimColors.TextPrimary,
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bubbleColor)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

/** 메시지 롱프레스 -> 신고 다이얼로그. 클릭 자체는 아무 동작이 없어(읽기 전용 채팅 로그)
 * `onClick = {}`으로 둔다. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.longPressable(onLongPress: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {},
        onLongClick = onLongPress,
    )
}

@Composable
private fun CancelQuickMessageDialog(
    reason: String,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(text = "정말 참가가 어려운가요?", style = DallimTypography.Title2, color = DallimColors.TextPrimary)
                Text(
                    text = "\"오늘 참가 어려워요\"를 보내면 참가가 즉시 취소돼요.",
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                )
                DallimTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = "취소 사유 (선택)",
                    placeholder = "예: 몸살이 나서 못 갈 것 같아요",
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                ) {
                    DallimTextButton(text = "취소", onClick = onDismiss)
                    DallimTextButton(text = "보내기", onClick = onConfirm)
                }
            }
        }
    }
}

/**
 * 메시지 롱프레스 액션 메뉴(2026-09-16 신규, 구현 7) — "신고하기"/"이 사용자 차단" 중 고른다.
 * 자기 자신 메시지에는 [canBlock]이 false로 넘어와 차단 항목이 숨는다.
 */
@Composable
private fun MessageActionDialog(
    canBlock: Boolean,
    onReportClick: () -> Unit,
    onBlockClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
            Column(modifier = Modifier.padding(Spacing.sm)) {
                DallimTextButton(text = "신고하기", onClick = onReportClick, modifier = Modifier.fillMaxWidth())
                if (canBlock) {
                    DallimTextButton(text = "이 사용자 차단", onClick = onBlockClick, modifier = Modifier.fillMaxWidth())
                }
                DallimTextButton(text = "취소", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ReportDialog(
    isMessageReport: Boolean,
    reason: String,
    isSubmitting: Boolean,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = DallimShapes.CardCorner, color = DallimColors.Surface) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(
                    text = if (isMessageReport) "메시지 신고" else "세션 신고",
                    style = DallimTypography.Title2,
                    color = DallimColors.TextPrimary,
                )
                DallimTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = "신고 사유 (선택)",
                    singleLine = false,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                ) {
                    DallimTextButton(text = "닫기", onClick = onDismiss, enabled = !isSubmitting)
                    DallimTextButton(
                        text = if (isSubmitting) "신고하는 중..." else "신고하기",
                        onClick = onSubmit,
                        enabled = !isSubmitting,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionChatScreenPreview() {
    DallimTheme {
        SocialSessionChatScreen(
            uiState = SocialSessionChatUiState.Success(
                messages = listOf(
                    ChatMessageItem(
                        id = "scm_1",
                        senderUserId = "usr_1",
                        senderNickname = "달림이",
                        senderAvatarId = "avatar_02",
                        type = SocialChatMessageType.HOST_ANNOUNCEMENT,
                        body = "오늘 우천으로 20분 늦게 시작합니다!",
                        createdAt = "2026-09-20T20:40:00Z",
                    ),
                    ChatMessageItem(
                        id = "scm_2",
                        senderUserId = "usr_2",
                        senderNickname = "러너B",
                        senderAvatarId = "avatar_05",
                        type = SocialChatMessageType.QUICK_MESSAGE,
                        body = "가는 중이에요",
                        createdAt = "2026-09-20T20:55:00Z",
                    ),
                    ChatMessageItem(
                        id = "scm_3",
                        senderUserId = null,
                        senderNickname = null,
                        senderAvatarId = null,
                        type = SocialChatMessageType.SYSTEM,
                        body = "러너C님이 참가를 취소했어요.",
                        createdAt = "2026-09-20T20:58:00Z",
                    ),
                ),
            ),
            onBackClick = {},
            onRetryClick = {},
            onDraftTextChange = {},
            onSendClick = {},
            onQuickMessageClick = {},
            onCancelReasonChange = {},
            onCancelQuickMessageDismiss = {},
            onCancelQuickMessageConfirm = {},
            onMessageLongPress = {},
            onActionMenuDismiss = {},
            onReportFromActionMenuClick = {},
            onBlockUserFromActionMenuClick = {},
            onSessionReportClick = {},
            onReportDialogDismiss = {},
            onReportReasonChange = {},
            onReportSubmit = {},
            onToastMessageShown = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionChatScreenExpiredPreview() {
    DallimTheme {
        SocialSessionChatScreen(
            uiState = SocialSessionChatUiState.Error(
                message = "채팅이 종료됐어요. 세션 시작 후 7일이 지나면 대화 내용을 볼 수 없어요.",
                isAccessExpired = true,
            ),
            onBackClick = {},
            onRetryClick = {},
            onDraftTextChange = {},
            onSendClick = {},
            onQuickMessageClick = {},
            onCancelReasonChange = {},
            onCancelQuickMessageDismiss = {},
            onCancelQuickMessageConfirm = {},
            onMessageLongPress = {},
            onActionMenuDismiss = {},
            onReportFromActionMenuClick = {},
            onBlockUserFromActionMenuClick = {},
            onSessionReportClick = {},
            onReportDialogDismiss = {},
            onReportReasonChange = {},
            onReportSubmit = {},
            onToastMessageShown = {},
        )
    }
}
