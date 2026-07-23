package com.learnspark.shared.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * LearnSpark 字体规范。
 *
 * - 主字体：跟随平台默认（Android: Roboto / Desktop: 系统默认）
 * - 标题：SemiBold，正文：Normal，辅助：Light
 * - 尺寸阶梯：32/24/20/16/14/12
 */
object LearnSparkTypography {
    private val baseFont = FontFamily.Default

    val DisplayLarge = TextStyle(fontFamily = baseFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp)
    val Headline = TextStyle(fontFamily = baseFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp)
    val TitleLarge = TextStyle(fontFamily = baseFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp)
    val TitleMedium = TextStyle(fontFamily = baseFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp)
    val Body = TextStyle(fontFamily = baseFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val BodySmall = TextStyle(fontFamily = baseFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val Caption = TextStyle(fontFamily = baseFont, fontWeight = FontWeight.Light, fontSize = 12.sp, lineHeight = 16.sp)
    val Button = TextStyle(fontFamily = baseFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)

    fun toMaterialTypography(): Typography = Typography(
        h1 = DisplayLarge,
        h2 = Headline,
        h3 = TitleLarge,
        h4 = TitleMedium,
        h5 = TitleMedium,
        h6 = TitleMedium,
        body1 = Body,
        body2 = BodySmall,
        caption = Caption,
        button = Button,
        subtitle1 = TitleMedium,
        subtitle2 = BodySmall,
    )
}
