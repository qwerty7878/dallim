package com.dallim.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 앱 전체 다크 고정 (docs/03-design-system.md §4, 2026-10-05 개정).
 *
 * 이전에는 "S-21만 다크 고정, 나머지는 시스템 설정 따라감"이었으나, 전면 다크 전환으로 분기를 없앴다 —
 * 라이트 스킴을 함께 유지하면 화면마다 두 벌의 대비를 맞춰야 해서 결국 어느 쪽도 다듬어지지 않는다.
 * [darkTheme] 파라미터는 호출부 호환을 위해 남겨두되 무시한다.
 *
 * Material 기본 컬러 롤(보랏빛 surfaceTint/surfaceContainer)이 드롭다운/다이얼로그/피커에 그대로
 * 비쳐 "기본 머티리얼" 인상을 주던 것은 계속 전부 앱 토큰으로 덮는다.
 */
private val DarkColors = darkColorScheme(
    primary = DallimColors.Primary,
    onPrimary = DallimColors.OnPrimary,
    primaryContainer = DallimColors.PrimaryLight,
    onPrimaryContainer = DallimColors.Primary,
    secondary = DallimColors.ActionFill,
    onSecondary = DallimColors.OnActionFill,
    secondaryContainer = DallimColors.SurfaceMuted,
    onSecondaryContainer = DallimColors.TextPrimary,
    tertiary = DallimColors.Primary,
    background = DallimColors.Background,
    onBackground = DallimColors.TextPrimary,
    surface = DallimColors.Surface,
    onSurface = DallimColors.TextPrimary,
    surfaceVariant = DallimColors.SurfaceMuted,
    onSurfaceVariant = DallimColors.TextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = DallimColors.Background,
    surfaceContainerLow = DallimColors.Surface,
    surfaceContainer = DallimColors.Surface,
    surfaceContainerHigh = DallimColors.SurfaceMuted,
    surfaceContainerHighest = DallimColors.SurfaceElevated,
    outline = DallimColors.Border,
    outlineVariant = DallimColors.Divider,
    error = DallimColors.Error,
    onError = DallimColors.OnActionFill,
    scrim = Color(0xCC000000),
)

@Composable
fun DallimTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = DallimMaterialTypography,
        shapes = DallimMaterialShapes,
        content = content,
    )
}
