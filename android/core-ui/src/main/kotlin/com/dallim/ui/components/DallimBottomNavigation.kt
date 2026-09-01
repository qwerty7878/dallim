package com.dallim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimShapes
import com.dallim.ui.theme.DallimTheme
import com.dallim.ui.theme.Spacing

/**
 * 홈(S-10)/탐색(S-11)/달림북(S-40) 3개 최상위 화면의 탭 (docs/01-feature-spec.md §1.0 표).
 * 비활성 상태는 outlined, 선택 상태는 filled 아이콘으로 표시한다.
 */
enum class DallimTab(
    val label: String,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
) {
    HOME("홈", Icons.Outlined.Home, Icons.Filled.Home),
    EXPLORE("탐색", Icons.Outlined.Search, Icons.Filled.Search),
    DALLIMBOOK("달림북", Icons.Outlined.MenuBook, Icons.Filled.MenuBook),
}

/**
 * 홈(S-10)/탐색(S-11)/달림북(S-40) 3개 최상위 화면 전용 하단 탭바 (docs/01-feature-spec.md §1.0,
 * docs/04-ui-guide.md §6). 그 외 화면(Route 상세, 저장한 코스, 러닝 플로우, 온보딩, 달림북 상세)에는
 * 쓰지 않는다 — 그 화면들은 지금처럼 push 이동만 한다.
 */
@Composable
fun DallimBottomNavigation(
    selectedTab: DallimTab,
    onTabSelected: (DallimTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DallimColors.Surface)
            .navigationBarsPadding()
            .height(DallimShapes.MinTapTarget), // 탭 타깃 최소 56dp (docs/03-design-system.md §3.3)
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DallimTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            // 활성/비활성 모두 단일 Primary 색만 사용 — 그라디언트 금지
            // (docs/03-design-system.md 그라디언트 적용 범위 표: "탭바" 는 적용 X).
            val tint = if (selected) DallimColors.Primary else DallimColors.TextSecondary
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onTabSelected(tab) },
            ) {
                Icon(
                    imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                    contentDescription = tab.label,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = tab.label,
                    fontSize = 11.sp,
                    color = tint,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DallimBottomNavigationPreview() {
    DallimTheme {
        DallimBottomNavigation(selectedTab = DallimTab.HOME, onTabSelected = {})
    }
}
