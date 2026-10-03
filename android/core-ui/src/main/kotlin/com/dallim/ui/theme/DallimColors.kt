package com.dallim.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

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
    // 2026-10-04 팔레트 개정(사용자 피드백 "쨍한 색 + 그라데이션이 AI 같다"): 형광에 가까운 보라(#4A3AFF)를
    // 채도를 낮춘 로열 블루로, 그라데이션은 전면 폐지(단색). 버튼은 Primary가 아니라 [Ink](거의 검정)다.
    val Primary = Color(0xFF3558C8) // 강조(경로선/링크/선택/지표) — 버튼 배경에는 쓰지 않는다
    val PrimaryDark = Color(0xFF263F96) // dark mode / 러닝 중 화면(S-21)
    val PrimaryLight = Color(0xFFEDF1FB) // 옅은 강조 배경
    /** 주 버튼 배경 — 거의 검정. 한 화면에 Primary 버튼 1개 규칙은 그대로. */
    val Ink = Color(0xFF1A1A1E)

    // 그라데이션 폐지 — 아래 두 토큰은 같은 색이라 기존 호출부가 자동으로 단색이 된다(2026-10-04).
    val GradientStart = Primary
    val GradientEnd = Primary

    // --- Route 상태 컬러 (섹션 1.3) ---
    val RouteDiscovery = Color(0xFF9E9E9E) // 중성 회색 — 아직 미검증
    val RouteVerified = Color(0xFF4A90D9) // 민트/블루 계열
    // RoutePopular has no dedicated hex — spec says "Primary(#4A3AFF) 배경 사용".
    val RoutePopular = Primary
    val RouteUnderReview = Color(0xFFF5A623) // 옐로우 경고

    // --- 시맨틱 컬러 (섹션 1.4) ---
    val Success = Color(0xFF1FA45A) // 2026-10-04: 밝은 연두(#2ECC71)는 연한 배경 위 글자 대비가 약해 한 톤 진하게
    val Warning = Color(0xFFF5A623)
    val Error = Color(0xFFE74C3C)

    val Background = Color(0xFFFFFFFF) // 라이트 모드 기본 (2026-10-04: 따뜻한 오프화이트 → 순백. 구분은 그림자가 아니라 면/구분선으로)
    val BackgroundDark = Color(0xFF0F0F14) // 러닝 중 다크모드
    val Surface = Color(0xFFFFFFFF)

    val TextPrimary = Color(0xFF1A1A1E)
    val TextSecondary = Color(0xFF6B6B75)
    val Border = Color(0xFFE5E5EA)

    // --- 2026-10-04 신규 중립 토큰 ---
    /** 카드/입력창/칩 등 "면" 채움 — 흰 배경 위에서 그림자 없이 영역을 구분한다. */
    val SurfaceMuted = Color(0xFFF4F4F6)
    /** 리스트 구분선(Border보다 옅음). */
    val Divider = Color(0xFFEDEDF0)
    /** 힌트/비활성 보조 텍스트. */
    val TextTertiary = Color(0xFF9A9AA4)
}

/** 2026-10-04: 그라데이션 폐지 — 이름은 호환용으로 남기고 단색 [Brush]를 돌려준다. */
val DallimGradient: Brush = SolidColor(DallimColors.Primary)
