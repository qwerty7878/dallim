package com.dallim.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography tokens from docs/03-design-system.md 섹션 2. Sizes/weights are transplanted
 * verbatim; font family defaults to system sans-serif until Pretendard is bundled as an
 * app asset (android-dev/ui-designer — see docs/03-design-system.md 2장 note).
 */
object DallimFontFamily {
    // TODO(android-dev): swap for the bundled Pretendard variable font.
    val Default = FontFamily.SansSerif
}

object DallimTypography {
    /** 40sp Bold — 결과 화면 거리 숫자(S-25) 전용. */
    val Display = TextStyle(fontFamily = DallimFontFamily.Default, fontSize = 40.sp, fontWeight = FontWeight.Bold)

    /** 24sp Bold — 화면 타이틀, Route 이름. */
    val Title1 = TextStyle(fontFamily = DallimFontFamily.Default, fontSize = 24.sp, fontWeight = FontWeight.Bold)

    /** 20sp SemiBold — 섹션 헤더. */
    val Title2 = TextStyle(fontFamily = DallimFontFamily.Default, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

    /** 16sp Regular — 본문. */
    val Body = TextStyle(fontFamily = DallimFontFamily.Default, fontSize = 16.sp, fontWeight = FontWeight.Normal)

    /** 13sp Regular — 보조 텍스트. */
    val Caption = TextStyle(fontFamily = DallimFontFamily.Default, fontSize = 13.sp, fontWeight = FontWeight.Normal)

    /**
     * 28sp Bold — 러닝 중(S-21) 거리/시간/페이스 전용.
     * S-21 예외 규칙(문서 2장): 최소 24sp 이상, 명암비 4.5:1 이상, 다크 배경 고정,
     * 색상만으로 정보 구분 금지(계획/실제 경로는 실선/점선 병행). 이 토큰을 쓰는 화면은
     * 해당 예외 규칙을 함께 지켜야 한다 — android-dev 구현 시 docs/04-ui-guide.md 확인.
     */
    val NavLarge = TextStyle(fontFamily = DallimFontFamily.Default, fontSize = 28.sp, fontWeight = FontWeight.Bold)
}

/** Maps DallimTypography onto Material3's Typography slots used across the app. */
val DallimMaterialTypography = Typography(
    displayLarge = DallimTypography.Display,
    headlineLarge = DallimTypography.Title1,
    titleLarge = DallimTypography.Title2,
    bodyLarge = DallimTypography.Body,
    labelSmall = DallimTypography.Caption,
)
