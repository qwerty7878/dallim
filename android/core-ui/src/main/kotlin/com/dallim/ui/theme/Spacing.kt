package com.dallim.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 8dp spacing scale (docs/04-ui-guide.md §1) — every screen padding/gap should reference these
 * tokens, not arbitrary `.dp` literals. `ScreenHorizontal` (20dp) is the one deliberate exception
 * called out in the guide.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** 화면 좌우 패딩 — the one fixed exception to the 8dp scale (§1 표). */
    val ScreenHorizontal = 20.dp

    /** 리스트 아이템 간 (§1 표). */
    val ListItemGap = 12.dp
}
