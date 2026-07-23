package com.learnspark.shared.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LearnSpark 主题（浅色 / 深色 / 跟随系统）。
 *
 * 用法：
 * ```kotlin
 * LearnSparkTheme {
 *     App()
 * }
 * ```
 *
 * 提供 [LocalSpacing] 用于统一的 4dp 阶梯间距。
 */
private val LightColors = lightColors(
    primary = LearnSparkColors.Primary,
    primaryVariant = LearnSparkColors.PrimaryPressed,
    secondary = LearnSparkColors.Info,
    background = LearnSparkColors.LightBackground,
    surface = LearnSparkColors.LightSurface,
    onPrimary = LearnSparkColors.OnPrimary,
    onSecondary = LearnSparkColors.OnPrimary,
    onBackground = LearnSparkColors.LightOnSurface,
    onSurface = LearnSparkColors.LightOnSurface,
    error = LearnSparkColors.Danger,
    onError = LearnSparkColors.OnPrimary,
)

private val DarkColors = darkColors(
    primary = LearnSparkColors.Primary,
    primaryVariant = LearnSparkColors.PrimaryPressed,
    secondary = LearnSparkColors.Info,
    background = LearnSparkColors.DarkBackground,
    surface = LearnSparkColors.DarkSurface,
    onPrimary = LearnSparkColors.OnPrimary,
    onSecondary = LearnSparkColors.OnPrimary,
    onBackground = LearnSparkColors.DarkOnSurface,
    onSurface = LearnSparkColors.DarkOnSurface,
    error = LearnSparkColors.Danger,
    onError = LearnSparkColors.OnPrimary,
)

/**
 * 统一间距阶梯（4dp 倍数）。
 */
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

enum class ThemeMode { System, Light, Dark }

@Composable
fun LearnSparkTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (isDark) DarkColors else LightColors

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalIsDarkTheme provides isDark,
    ) {
        MaterialTheme(
            colors = colors,
            typography = LearnSparkTypography.toMaterialTypography(),
            content = content,
        )
    }
}

object LearnSparkTokens {
    val conflictColor: Color
        @Composable get() = LearnSparkColors.Warning

    val dangerColor: Color
        @Composable get() = LearnSparkColors.Danger

    val onSurfaceMuted: Color
        @Composable get() = if (LocalIsDarkTheme.current) LearnSparkColors.DarkOnSurfaceMuted
        else LearnSparkColors.LightOnSurfaceMuted
}
