package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * Agreement checkbox row for S-03 약관 동의 — a circular check indicator + label, with an
 * optional trailing chevron to open the terms detail. Custom, not Material3's `Checkbox`
 * (docs/04-ui-guide.md §6 "Material3 기본 컴포넌트 그대로 쓰지 말고 커스텀").
 */
@Composable
fun DallimCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = true,
    onDetailClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DallimShapes.MinTapTarget)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) DallimColors.Primary else Color.Transparent)
                .border(1.dp, if (checked) DallimColors.Primary else DallimColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Text(
            text = if (required) "[필수] $label" else "[선택] $label",
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.sm),
        )

        if (onDetailClick != null) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "약관 전문 보기",
                tint = DallimColors.TextSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onDetailClick() },
            )
        }
    }
}
