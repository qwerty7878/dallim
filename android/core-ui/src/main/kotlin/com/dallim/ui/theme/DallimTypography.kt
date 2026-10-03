package com.dallim.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dallim.ui.R

/**
 * Typography tokens (docs/03-design-system.md 섹션 2, 2026-10-04 개정).
 *
 * 서체는 Pretendard(SIL OFL, `core-ui/PRETENDARD_LICENSE.txt`) 4굵기를 번들한다 — 시스템 기본
 * 산세리프는 "기본값 앱" 인상의 가장 큰 원인이었다. 모든 스타일에 행간(lineHeight)과 약한 음의
 * 자간(한글 가독성/밀도)을 지정한다.
 */
object DallimFontFamily {
    val Default = FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_medium, FontWeight.Medium),
        Font(R.font.pretendard_semibold, FontWeight.SemiBold),
        Font(R.font.pretendard_bold, FontWeight.Bold),
    )
}

object DallimTypography {
    /** 40sp Bold — 결과 화면 거리 숫자 등 "숫자가 주인공"인 곳. */
    val Display = TextStyle(
        fontFamily = DallimFontFamily.Default, fontSize = 40.sp, lineHeight = 46.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em,
    )

    /** 24sp Bold — 화면 타이틀, Route 이름. */
    val Title1 = TextStyle(
        fontFamily = DallimFontFamily.Default, fontSize = 24.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em,
    )

    /** 20sp SemiBold — 섹션 헤더. */
    val Title2 = TextStyle(
        fontFamily = DallimFontFamily.Default, fontSize = 20.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.015).em,
    )

    /** 17sp SemiBold — 리스트 항목 제목/카드 제목(2026-10-04 신규, Body와 위계를 가르기 위함). */
    val Title3 = TextStyle(
        fontFamily = DallimFontFamily.Default, fontSize = 17.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em,
    )

    /** 15sp Regular — 본문(2026-10-04 16→15sp: 리스트 위주 화면에서 16sp는 투박했다). */
    val Body = TextStyle(
        fontFamily = DallimFontFamily.Default, fontSize = 15.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.005).em,
    )

    /** 13sp Regular — 보조 텍스트. */
    val Caption = TextStyle(
        fontFamily = DallimFontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 12sp Medium — 배지/칩/탭 라벨(2026-10-04 신규). */
    val Label = TextStyle(
        fontFamily = DallimFontFamily.Default, fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    )

    /**
     * 28sp Bold — 러닝 중(S-21) 거리/시간/페이스 전용.
     * S-21 예외 규칙(문서 2장): 최소 24sp 이상, 명암비 4.5:1 이상, 다크 배경 고정,
     * 색상만으로 정보 구분 금지(계획/실제 경로는 실선/점선 병행).
     */
    val NavLarge = TextStyle(
        fontFamily = DallimFontFamily.Default, fontSize = 28.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em,
    )
}

/**
 * Material3 슬롯 전부에 Pretendard를 적용한다 — 스타일 없이 쓰는 `Text`/Material 컴포넌트가
 * `LocalTextStyle`(bodyLarge)로 시스템 폰트를 그리는 일이 없게 한다.
 */
val DallimMaterialTypography: Typography = Typography().let { base ->
    fun TextStyle.withFont() = copy(fontFamily = DallimFontFamily.Default)
    base.copy(
        displayLarge = DallimTypography.Display,
        displayMedium = DallimTypography.Display.copy(fontSize = 34.sp, lineHeight = 40.sp),
        displaySmall = DallimTypography.Title1,
        headlineLarge = DallimTypography.Title1,
        headlineMedium = DallimTypography.Title2,
        headlineSmall = DallimTypography.Title3,
        titleLarge = DallimTypography.Title2,
        titleMedium = DallimTypography.Title3,
        titleSmall = DallimTypography.Body.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = DallimTypography.Body,
        bodyMedium = DallimTypography.Body,
        bodySmall = DallimTypography.Caption,
        labelLarge = DallimTypography.Body.copy(fontWeight = FontWeight.Medium),
        labelMedium = DallimTypography.Label,
        labelSmall = DallimTypography.Label,
    )
}
