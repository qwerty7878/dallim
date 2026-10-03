package com.dallim.app.route.create

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dallim.ui.components.DallimCard
import com.dallim.ui.components.DallimTopBar
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * S-43 코스 만들기 진입점 — 탐색(S-11) FAB에서 진입, "직접 그리기"(S-44) / "AI로 자동 생성"(S-45)
 * 중 하나를 고른다. docs/01-feature-spec.md에는 없던 화면(오케스트레이터 지시로 신설) —
 * MVP2 예정이던 코스 생성을 docs/02-api-spec.md 8장 API에 맞춰 조기 구현한다.
 */
@Composable
fun CourseCreateEntryRoute(
    onBackClick: () -> Unit,
    onDrawClick: () -> Unit,
    onAiGenerateClick: () -> Unit,
) {
    CourseCreateEntryScreen(
        onBackClick = onBackClick,
        onDrawClick = onDrawClick,
        onAiGenerateClick = onAiGenerateClick,
    )
}

@Composable
private fun CourseCreateEntryScreen(
    onBackClick: () -> Unit,
    onDrawClick: () -> Unit,
    onAiGenerateClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DallimColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        DallimTopBar(title = "코스 만들기", onBackClick = onBackClick)

        Text(
            text = "어떤 방법으로 만들까요?",
            style = DallimTypography.Body,
            color = DallimColors.TextSecondary,
            modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.md),
        )

        Column(
            modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CreateOptionCard(
                icon = DallimIcons.Pencil,
                title = "직접 그리기",
                description = "지도 위에 손가락으로 그리면 실제 도로에 맞게 변환해드려요.",
                onClick = onDrawClick,
            )
            CreateOptionCard(
                icon = DallimIcons.Sparkles,
                title = "AI로 자동 생성",
                description = "원하는 거리만 정하면 출발점으로 돌아오는 코스를 만들어드려요.",
                onClick = onAiGenerateClick,
            )
        }
    }
}

@Composable
private fun CreateOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    DallimCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = DallimColors.TextPrimary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.padding(start = Spacing.md).weight(1f)) {
                Text(text = title, style = DallimTypography.Title3, color = DallimColors.TextPrimary)
                Text(
                    text = description,
                    style = DallimTypography.Caption,
                    color = DallimColors.TextSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Icon(imageVector = DallimIcons.ChevronRight, contentDescription = null, tint = DallimColors.TextTertiary)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CourseCreateEntryScreenPreview() {
    DallimTheme {
        CourseCreateEntryScreen(onBackClick = {}, onDrawClick = {}, onAiGenerateClick = {})
    }
}
