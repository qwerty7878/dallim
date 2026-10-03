package com.dallim.app.running.share

/** S-26 공유 카드 배경 옵션 — 2026-10-04: 그라디언트 폐지, 라이트/다크 두 가지(지도 이미지는 항상 포함). */
enum class ShareCardBackground { LIGHT, DARK }

enum class ShareCardRatio(val widthPx: Int, val heightPx: Int, val label: String) {
    SQUARE(1080, 1080, "1:1"),
    STORY(1080, 1920, "9:16"),
}

data class ShareCardOptions(
    val background: ShareCardBackground = ShareCardBackground.LIGHT,
    val ratio: ShareCardRatio = ShareCardRatio.SQUARE,
    val showDistance: Boolean = true,
    val showDuration: Boolean = true,
    val showPace: Boolean = true,
)
