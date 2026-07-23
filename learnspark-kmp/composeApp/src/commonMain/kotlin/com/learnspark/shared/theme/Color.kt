package com.learnspark.shared.theme

import androidx.compose.ui.graphics.Color

/**
 * LearnSpark 品牌色。
 *
 * 主色调：绿色（学习、生机），辅助色：琥珀（冲突提醒）、天蓝（信息）。
 *
 * 浅色 / 深色主题共用同一品牌色，根据主题调整 surface / onSurface 对比度。
 */
object LearnSparkColors {
    // Brand
    val Primary = Color(0xFF18A058)        // 学习主色（与 Vue 端一致）
    val PrimaryHover = Color(0xFF36AD6A)
    val PrimaryPressed = Color(0xFF0C7A43)
    val OnPrimary = Color(0xFFFFFFFF)

    // Semantic
    val Success = Color(0xFF18A058)
    val Warning = Color(0xFFF59E0B)        // 冲突副本琥珀色
    val Danger = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)

    // Light surface
    val LightBackground = Color(0xFFF7F8FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF1F2329)
    val LightOnSurfaceMuted = Color(0xFF6B7280)
    val LightOutline = Color(0xFFE5E7EB)

    // Dark surface
    val DarkBackground = Color(0xFF101418)
    val DarkSurface = Color(0xFF181D23)
    val DarkSurfaceVariant = Color(0xFF252B33)
    val DarkOnSurface = Color(0xFFE5E7EB)
    val DarkOnSurfaceMuted = Color(0xFF9CA3AF)
    val DarkOutline = Color(0xFF374151)
}
