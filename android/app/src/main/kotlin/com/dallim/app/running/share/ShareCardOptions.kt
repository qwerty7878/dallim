package com.dallim.app.running.share

/** S-26 공유 카드 배경 옵션 — 시그니처 그라디언트는 허용된 4곳 중 하나(docs/04-ui-guide.md §3). */
enum class ShareCardBackground { LIGHT, DARK, GRADIENT }

enum class ShareCardRatio(val widthPx: Int, val heightPx: Int, val label: String) {
    SQUARE(1080, 1080, "1:1"),
    STORY(1080, 1920, "9:16"),
}

data class ShareCardOptions(
    val background: ShareCardBackground = ShareCardBackground.GRADIENT,
    val ratio: ShareCardRatio = ShareCardRatio.SQUARE,
    val showDistance: Boolean = true,
    val showDuration: Boolean = true,
    val showPace: Boolean = true,
)
