package com.learnspark.shared.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 阶段 3.3：骨架屏（按文档 §3.3 主题与动画）。
 *
 * 用 shimmer 渐变动画 + 占位条降低感知等待时间。
 * 适用于：列表加载、详情页解析、文件上传进度等待等场景。
 */
@Composable
fun SkeletonBar(
    width: Dp? = null,
    height: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    val baseMod = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(
        modifier = baseMod
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(androidx.compose.material.MaterialTheme.colors.onSurface.copy(alpha = alpha)),
    )
}

/**
 * 列表项骨架（图标 + 双行文本）。
 */
@Composable
fun SkeletonListItem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        SkeletonBar(width = 40.dp, height = 40.dp, modifier = Modifier.clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            SkeletonBar(height = 14.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonBar(width = 160.dp, height = 12.dp)
        }
    }
}
