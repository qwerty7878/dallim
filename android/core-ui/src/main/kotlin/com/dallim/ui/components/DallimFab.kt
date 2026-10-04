package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * 하단 플로팅 액션 — Material `ExtendedFloatingActionButton`(둥근 알약 + 그림자 + 보라 채움) 대신 거의 검정 면의
 * 작은 라운드 버튼(2026-10-04). 화면의 주 생성 행동 하나에만 쓴다.
 */
@Composable
fun DallimFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(DallimColors.ActionFill)
            .clickable(onClick = onClick)
            .height(48.dp)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = DallimColors.OnActionFill, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(text = text, style = DallimTypography.Title3, color = DallimColors.OnActionFill)
    }
}

/** 하단 안내 메시지 — Material 기본 스낵바 대신 거의 검정 바탕/흰 글자의 라운드 박스. */
@Composable
fun DallimSnackbar(data: SnackbarData) {
    Snackbar(
        modifier = Modifier.padding(Spacing.md),
        shape = RoundedCornerShape(12.dp),
        containerColor = DallimColors.ActionFill,
        contentColor = DallimColors.OnActionFill,
    ) {
        Text(text = data.visuals.message, style = DallimTypography.Body, color = DallimColors.OnActionFill)
    }
}
