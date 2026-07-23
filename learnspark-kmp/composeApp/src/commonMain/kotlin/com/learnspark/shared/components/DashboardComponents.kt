package com.learnspark.shared.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnspark.shared.theme.LearnSparkColors
import com.learnspark.shared.theme.LocalIsDarkTheme

/**
 * 仪表盘风格的可复用 UI 组件。
 *
 * 设计语言：深色卡片 + 12dp 圆角 + 半透明图标徽章 + 单色调点缀。
 * 同时适配深色 / 浅色主题（卡片底色随主题切换）。
 */

/** 屏幕顶部栏：汉堡菜单 + 标题 + 桌面切换 + 头像。 */
@Composable
fun ScreenTopBar(
    title: String,
    onMenuClick: () -> Unit = {},
    onDesktopToggle: (() -> Unit)? = null,
    onAvatarClick: () -> Unit = {},
    avatarLabel: String = "我",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Menu,
            contentDescription = "菜单",
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onMenuClick),
            tint = MaterialTheme.colors.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.h6,
            modifier = Modifier.weight(1f),
        )
        if (onDesktopToggle != null) {
            Icon(
                Icons.Outlined.Computer,
                contentDescription = "桌面端",
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onDesktopToggle),
                tint = MaterialTheme.colors.onSurface,
            )
            Spacer(Modifier.width(12.dp))
        }
        AvatarChip(label = avatarLabel, onClick = onAvatarClick)
    }
}

@Composable
fun AvatarChip(
    label: String,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colors.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = MaterialTheme.colors.onPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 通用卡片容器：圆角 + 主题表面色。 */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = if (LocalIsDarkTheme.current) 0.dp else 1.dp,
    ) {
        content()
    }
}

/**
 * 单个统计块（截图里"待办任务 / 连续打卡 / 总积分"那一格）。
 * 图标徽章 + 小标题 + 大数字 + 备注行。
 */
@Composable
fun MetricTile(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    label: String,
    value: String,
    suffix: String? = null,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    StatCard(modifier = cardModifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold,
                )
                if (suffix != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        suffix,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/**
 * 圆环进度指示器。空心圆 + 弧形进度 + 中心百分比。
 *
 * [progress] 范围 0..1。
 */
@Composable
fun CircularProgressRing(
    progress: Float,
    centerTopText: String? = null,
    centerBottomText: String? = null,
    size: androidx.compose.ui.unit.Dp = 96.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 8.dp,
    trackColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
    progressColor: Color = MaterialTheme.colors.primary,
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(this.size.width - strokeWidth.toPx(), this.size.height - strokeWidth.toPx())
            // 背景圆环
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            // 进度弧
            val sweep = 360f * progress.coerceIn(0f, 1f)
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (centerTopText != null) {
                Text(
                    centerTopText,
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (centerBottomText != null) {
                Text(
                    centerBottomText,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 截图里那种顶部细线 banner（如"AI 灵感"）。 */
@Composable
fun InsightBanner(
    text: String,
    source: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StatCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("“", style = MaterialTheme.typography.h4, color = MaterialTheme.colors.primary)
            Text(text, style = MaterialTheme.typography.body1)
            if (source != null) {
                Text(
                    "— LearnSpark · $source",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            }
            if (onAction != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAction) { Text("换一句") }
            }
        }
    }
}

/** 截图里"待办任务"标题行：彩色块 + 文字 + 角标 + 进度。 */
@Composable
fun SectionHeader(
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colors.primary,
    title: String,
    badge: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Medium)
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(badge, color = MaterialTheme.colors.primary, style = MaterialTheme.typography.caption)
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

/** 通用 action 按钮（绿底白字或青底白字的圆角按钮）。 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    background: Color = MaterialTheme.colors.primary,
    foreground: Color = MaterialTheme.colors.onPrimary,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = foreground, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = foreground, style = MaterialTheme.typography.button)
    }
}

/** 标准容器色 token：浅色 / 深色自动切换（用于卡片背景徽章）。 */
val TaskIconBackgrounds = listOf(
    LearnSparkColors.Primary.copy(alpha = 0.15f),     // 绿
    LearnSparkColors.Info.copy(alpha = 0.18f),         // 蓝
    LearnSparkColors.Warning.copy(alpha = 0.18f),      // 琥珀
)
