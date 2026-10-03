package com.dallim.ui.components

import com.dallim.ui.icons.DallimIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing

/**
 * 현재 선택값을 텍스트 + ▾로 보여주고 탭하면 메뉴가 열리는 저강조 단일 선택 컨트롤.
 * 칩 여러 줄(필터 3~4줄이 화면 1/3을 차지하던 문제, docs/04-ui-guide.md §2 위계)을 대체한다 —
 * S-11 탐색과 대회 목록의 보조 필터(상태/종목/정렬)가 공용으로 쓴다.
 */
@Composable
fun DallimDropdownText(
    label: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.clickable { expanded = true }.padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = DallimTypography.Caption, color = DallimColors.TextPrimary)
            Icon(
                imageVector = DallimIcons.ChevronDown,
                contentDescription = null,
                tint = DallimColors.TextSecondary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(text = option, style = DallimTypography.Body) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}
