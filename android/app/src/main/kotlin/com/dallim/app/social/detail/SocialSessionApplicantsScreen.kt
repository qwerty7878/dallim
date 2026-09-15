package com.dallim.app.social.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dallim.app.onboarding.profile.ComfortablePace
import com.dallim.app.onboarding.profile.RunningExperience
import com.dallim.app.social.SocialAvatarBadge
import com.dallim.app.social.SocialSessionFormat
import com.dallim.network.social.SocialSessionApplicantItem
import com.dallim.network.social.SocialSessionApplicantStatus
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimErrorState
import com.dallim.ui.components.DallimLoadingState
import com.dallim.ui.components.DallimPrimaryButton
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-34 호스트 — 신청자 관리 (docs/달림_화면별_상세기획서_v1.3.md PART 3-D, docs/02-api-spec.md
 * 17.6/17.7). 성별/나이/연락처는 응답에 없으니 표시하지 않는다(SPEC 명시, 응답 DTO 자체에도
 * 없음 — CLAUDE.md 규칙 2). 참석률/소셜 달림 횟수/긍정 행동 태그/과거 동반 여부는 2단계
 * (체크인/피드백)가 있어야 계산 가능해 이번 응답에 없다 — 없는 데이터를 placeholder로 채우지
 * 않는다(작업 브리핑 참고).
 */
@Composable
fun SocialSessionApplicantsRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SocialSessionApplicantsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SocialSessionApplicantsScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetryClick = viewModel::load,
        onApproveClick = viewModel::onApproveClick,
        modifier = modifier,
    )
}

@Composable
private fun SocialSessionApplicantsScreen(
    uiState: SocialSessionApplicantsUiState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onApproveClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = DallimColors.TextPrimary,
                )
            }
            Text(text = "신청자 관리", style = DallimTypography.Title1, color = DallimColors.TextPrimary)
        }

        when (uiState) {
            SocialSessionApplicantsUiState.Loading -> DallimLoadingState(modifier = Modifier.weight(1f))
            is SocialSessionApplicantsUiState.Error -> DallimErrorState(
                title = "신청자 목록을 불러오지 못했어요",
                description = uiState.message,
                onRetry = onRetryClick,
                modifier = Modifier.weight(1f),
            )
            is SocialSessionApplicantsUiState.Success -> {
                if (uiState.items.isEmpty()) {
                    DallimErrorState(
                        title = "아직 신청자가 없어요",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = Spacing.ScreenHorizontal,
                            end = Spacing.ScreenHorizontal,
                            top = Spacing.sm,
                            bottom = Spacing.xl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        if (uiState.actionErrorMessage != null) {
                            item {
                                Text(
                                    text = uiState.actionErrorMessage,
                                    style = DallimTypography.Caption,
                                    color = DallimColors.Error,
                                    modifier = Modifier.padding(bottom = Spacing.xs),
                                )
                            }
                        }
                        items(uiState.items, key = { it.userId }) { applicant ->
                            ApplicantCard(
                                applicant = applicant,
                                isApproving = uiState.approvingUserId == applicant.userId,
                                onApproveClick = { onApproveClick(applicant.userId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicantCard(
    applicant: SocialSessionApplicantItem,
    isApproving: Boolean,
    onApproveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DallimCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialAvatarBadge(avatarId = applicant.avatarId, size = 44.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(text = applicant.nickname, style = DallimTypography.Body, color = DallimColors.TextPrimary)
                Text(
                    text = SocialSessionFormat.displayTemperature(applicant.runningTemperature),
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                )
            }
            ApplicantStatusTag(status = applicant.status)
        }

        val paceLabel = comfortablePaceLabel(applicant.comfortablePace)
        val experienceLabel = runningExperienceLabel(applicant.runningExperience)
        if (paceLabel != null || experienceLabel != null) {
            Row(modifier = Modifier.padding(top = Spacing.sm)) {
                if (paceLabel != null) {
                    Text(text = "편안한 페이스 $paceLabel", style = DallimTypography.Caption, color = DallimColors.TextSecondary)
                }
                if (paceLabel != null && experienceLabel != null) {
                    Text(text = " · ", style = DallimTypography.Caption, color = DallimColors.TextSecondary)
                }
                if (experienceLabel != null) {
                    Text(text = "러닝 경험 $experienceLabel", style = DallimTypography.Caption, color = DallimColors.TextSecondary)
                }
            }
        }

        val message = applicant.message
        if (!message.isNullOrBlank()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm), color = DallimColors.Border)
            Text(text = message, style = DallimTypography.Body, color = DallimColors.TextPrimary)
        }

        Text(
            text = "신청 ${SocialSessionFormat.displayAppliedAt(applicant.appliedAt)}",
            style = DallimTypography.Caption,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(top = Spacing.sm),
        )

        if (applicant.status == SocialSessionApplicantStatus.PENDING) {
            // 화면 진입/새로고침 시점 1회 계산 — 실시간 카운트다운은 과설계(작업 브리핑 지시).
            Text(
                text = "호스트 응답 ${SocialSessionFormat.respondByRemainingLabel(applicant.respondByAt)}",
                style = DallimTypography.Caption,
                color = DallimColors.Warning,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            DallimPrimaryButton(
                text = if (isApproving) "승인하는 중..." else "승인",
                onClick = onApproveClick,
                enabled = !isApproving,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
    }
}

@Composable
private fun ApplicantStatusTag(status: String, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (status) {
        SocialSessionApplicantStatus.APPROVED -> Triple(DallimColors.Success.copy(alpha = 0.15f), DallimColors.Success, "승인됨")
        SocialSessionApplicantStatus.EXPIRED -> Triple(DallimColors.TextSecondary.copy(alpha = 0.12f), DallimColors.TextSecondary, "만료됨")
        SocialSessionApplicantStatus.CANCELLED -> Triple(DallimColors.TextSecondary.copy(alpha = 0.12f), DallimColors.TextSecondary, "취소함")
        else -> Triple(DallimColors.Warning.copy(alpha = 0.15f), DallimColors.Warning, "대기중")
    }
    Text(
        text = label,
        style = DallimTypography.Caption,
        color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

/** [applicant.comfortablePace]는 자유 텍스트 컬럼이라(SocialSession.kt 문서 참고) 알려진
 * [ComfortablePace.apiValue]와 일치하면 한국어 라벨로, 아니면 원본 값을 그대로 보여준다. */
private fun comfortablePaceLabel(apiValue: String?): String? =
    apiValue?.let { value -> ComfortablePace.entries.firstOrNull { it.apiValue == value }?.label ?: value }

private fun runningExperienceLabel(apiValue: String?): String? =
    apiValue?.let { value -> RunningExperience.entries.firstOrNull { it.apiValue == value }?.label ?: value }

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionApplicantsScreenPreview() {
    DallimTheme {
        SocialSessionApplicantsScreen(
            uiState = SocialSessionApplicantsUiState.Success(
                items = listOf(
                    SocialSessionApplicantItem(
                        userId = "usr_2",
                        nickname = "러너B",
                        avatarId = "avatar_01",
                        runningTemperature = 36.8,
                        comfortablePace = "PACE_6_7",
                        runningExperience = "MONTHS_3_TO_12",
                        message = "초보인데 같이 뛰어도 될까요?",
                        appliedAt = "2026-09-14T10:00:00Z",
                        status = "PENDING",
                        respondByAt = "2026-09-14T15:00:00Z",
                    ),
                    SocialSessionApplicantItem(
                        userId = "usr_3",
                        nickname = "숲속러너",
                        avatarId = null,
                        runningTemperature = 38.1,
                        comfortablePace = null,
                        runningExperience = "OVER_1_YEAR",
                        message = null,
                        appliedAt = "2026-09-13T09:00:00Z",
                        status = "APPROVED",
                    ),
                ),
            ),
            onBackClick = {},
            onRetryClick = {},
            onApproveClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SocialSessionApplicantsScreenEmptyPreview() {
    DallimTheme {
        SocialSessionApplicantsScreen(
            uiState = SocialSessionApplicantsUiState.Success(items = emptyList()),
            onBackClick = {},
            onRetryClick = {},
            onApproveClick = {},
        )
    }
}
