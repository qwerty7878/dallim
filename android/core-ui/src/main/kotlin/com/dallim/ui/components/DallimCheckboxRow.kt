package com.dallim.ui.components

import com.dallim.ui.icons.DallimIcons
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
 * Custom checkbox row — a circular check indicator + label. Not Material3's `Checkbox`
 * (docs/04-ui-guide.md §6 "Material3 기본 컴포넌트 그대로 쓰지 말고 커스텀").
 *
 * Two shapes in one component:
 * - `required = null`(기본값): 접두어 없는 단순 토글 — 예: S-44 "출발점으로 돌아오기".
 * - `required = true/false`: S-03 약관 동의처럼 "[필수]"/"[선택]" 접두어를 붙이고, 필요하면
 *   `onDetailClick`으로 약관 전문 보기 화살표를 함께 보여준다.
 */
@Composable
fun DallimCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean? = null,
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
                    imageVector = DallimIcons.Check,
                    contentDescription = null,
                    tint = DallimColors.OnPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Text(
            text = when (required) {
                true -> "[필수] $label"
                false -> "[선택] $label"
                null -> label
            },
            style = DallimTypography.Body,
            color = DallimColors.TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.sm),
        )

        if (onDetailClick != null) {
            Icon(
                imageVector = DallimIcons.ChevronRight,
                contentDescription = "약관 전문 보기",
                tint = DallimColors.TextSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onDetailClick() },
            )
        }
    }
}
