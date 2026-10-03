package com.dallim.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 라이트/다크 컬러 스킴. docs/03-design-system.md 섹션 4:
 * "그 외 화면은 시스템 설정 따라감(라이트 기본)". S-21(러닝 중)은 이 테마의 시스템 다크모드
 * 분기와 무관하게 항상 다크 고정이어야 하므로, 그 화면은 android-dev가 별도로
 * `forceDark = true` 같은 파라미터를 이 테마 진입점에 추가해 처리한다(여기서는 스캐폴딩만).
 */
// 2026-10-04: Material 기본 컬러 롤(보랏빛 surfaceTint/surfaceContainer)이 드롭다운/다이얼로그/피커에 그대로
// 비쳐 "기본 머티리얼" 인상을 주던 것을 전부 앱 토큰으로 덮었다.
private val LightColors = lightColorScheme(
    primary = DallimColors.Primary,
    onPrimary = DallimColors.Surface,
    primaryContainer = DallimColors.PrimaryLight,
    onPrimaryContainer = DallimColors.Primary,
    secondary = DallimColors.Ink,
    onSecondary = DallimColors.Surface,
    secondaryContainer = DallimColors.SurfaceMuted,
    onSecondaryContainer = DallimColors.TextPrimary,
    tertiary = DallimColors.Primary,
    background = DallimColors.Background,
    onBackground = DallimColors.TextPrimary,
    surface = DallimColors.Surface,
    onSurface = DallimColors.TextPrimary,
    surfaceVariant = DallimColors.SurfaceMuted,
    onSurfaceVariant = DallimColors.TextSecondary,
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
    surfaceContainerLowest = DallimColors.Surface,
    surfaceContainerLow = DallimColors.Surface,
    surfaceContainer = DallimColors.Surface,
    surfaceContainerHigh = DallimColors.Surface,
    surfaceContainerHighest = DallimColors.SurfaceMuted,
    outline = DallimColors.Border,
    outlineVariant = DallimColors.Divider,
    error = DallimColors.Error,
)

private val DarkColors = darkColorScheme(
    primary = DallimColors.PrimaryDark,
    background = DallimColors.BackgroundDark,
    surface = DallimColors.BackgroundDark,
    onBackground = DallimColors.Surface,
    onSurface = DallimColors.Surface,
    error = DallimColors.Error,
)

@Composable
fun DallimTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DallimMaterialTypography,
        shapes = DallimMaterialShapes,
        content = content,
    )
}
