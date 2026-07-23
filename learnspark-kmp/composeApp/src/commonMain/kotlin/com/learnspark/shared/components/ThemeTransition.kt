package com.learnspark.shared.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import com.learnspark.shared.theme.LocalIsDarkTheme
import com.learnspark.shared.theme.ThemeMode

/**
 * 阶段 3.3：主题切换动画（按文档 §3.3 主题与动画）。
 *
 * 包裹内容，当 [themeMode] 变化时整体淡入淡出（200ms），
 * 避免 Material colors 突变带来的视觉跳变。
 */
@Composable
fun ThemeTransition(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = themeMode,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(animationSpec = tween(200)),
                initialContentExit = fadeOut(animationSpec = tween(200)),
            )
        },
        label = "theme-transition",
    ) { _ ->
        content()
    }
}
