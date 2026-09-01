package com.dallim.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Design tokens transplanted verbatim from docs/03-design-system.md (섹션 1, 5).
 * Do NOT hardcode color hex values anywhere outside this file — every screen references
 * these tokens only (docs/04-ui-guide.md, docs/03-design-system.md 섹션 5 note, CLAUDE.md rule 6).
 *
 * Gradient usage is restricted to emotional moments (GPS trail, app icon, result screen,
 * splash/onboarding, share card) — NOT buttons/text links/icons/form fields/list items/tab bar.
 * See docs/03-design-system.md 1.2 표 for the full allow/deny list before using DallimGradient.
 */
object DallimColors {
    // --- Primary (섹션 1.2) ---
    val Primary = Color(0xFF4A3AFF)
    val PrimaryDark = Color(0xFF2E22C7) // dark mode / 러닝 중 화면(S-21)
    val PrimaryLight = Color(0xFFEAE8FF) // 배경 강조, 선택 상태

    val GradientStart = Color(0xFF4A3AFF)
    val GradientEnd = Color(0xFFFF6B4A)

    // --- Route 상태 컬러 (섹션 1.3) ---
    val RouteDiscovery = Color(0xFF9E9E9E) // 중성 회색 — 아직 미검증
    val RouteVerified = Color(0xFF4A90D9) // 민트/블루 계열
    // RoutePopular has no dedicated hex — spec says "Primary(#4A3AFF) 배경 사용".
    val RoutePopular = Primary
    val RouteUnderReview = Color(0xFFF5A623) // 옐로우 경고

    // --- 시맨틱 컬러 (섹션 1.4) ---
    val Success = Color(0xFF2ECC71)
    val Warning = Color(0xFFF5A623)
    val Error = Color(0xFFE74C3C)

    val Background = Color(0xFFFAFAF8) // 라이트 모드 기본
    val BackgroundDark = Color(0xFF0F0F14) // 러닝 중 다크모드
    val Surface = Color(0xFFFFFFFF)

    val TextPrimary = Color(0xFF1A1A1E)
    val TextSecondary = Color(0xFF6B6B75)
    val Border = Color(0xFFE5E5EA)
}

/**
 * Signature gradient (블루바이올렛 -> 코랄). See DallimColors doc comment for where this is
 * (and is not) allowed to be used.
 */
val DallimGradient = Brush.linearGradient(
    colors = listOf(DallimColors.GradientStart, DallimColors.GradientEnd),
)
