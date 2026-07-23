package com.learnspark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.learnspark.features.notification.ReminderViewModel
import com.learnspark.data.sync.SyncManager
import com.learnspark.shared.components.ResponsiveAppLayout
import com.learnspark.shared.components.ThemeTransition
import com.learnspark.shared.theme.LearnSparkTheme
import com.learnspark.shared.theme.ThemeMode
import org.koin.compose.koinInject

/**
 * 主题模式 CompositionLocal（阶段 1.1.6）。
 *
 * Settings 页面通过 `LocalThemeMode` 写入新值，整个 App 树重新组合生效。
 */
val LocalThemeMode = compositionLocalOf { ThemeMode.System }

/**
 * 占位 AppGraph（阶段 1.1.6 兼容保留）。
 *
 * 阶段 1.1.6 起优先用 Koin `koinInject<T>()`；旧 Screen 仍可通过
 * `LocalAppGraph.current` 拿到依赖（由 Koin 注入回填）。
 */
data class AppGraph(
    val repository: com.learnspark.data.db.ProjectRepository,
    val api: com.learnspark.data.api.LearnSparkApi?,
)

val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error(
        "AppGraph not provided. Wrap content in App() or CompositionLocalProvider(LocalAppGraph provides ...). " +
            "阶段 1.1.6 起推荐使用 Koin koinInject<>() 注入依赖。"
    )
}

/**
 * 应用入口。
 *
 * 阶段 1.1.6 职责：
 * - 应用 LearnSparkTheme（浅色 / 深色 / 跟随系统）
 * - 根据屏幕宽度自适应布局：Desktop 侧边栏 / Mobile 底部导航
 *
 * 阶段 3.3 增强：
 * - ThemeTransition：主题切换时整体淡入淡出 200ms
 *
 * 阶段 R3b 增强：
 * - 启动时构造 ReminderViewModel 触发后台轮询（拉取提醒日志 → 平台通知）
 *
 * 依赖（Repository / Api）由 Koin 注入，参见 `core.di.coreModule`。
 */
@Composable
fun App(
    themeMode: ThemeMode = ThemeMode.System,
) {
    // 阶段 R3b：主动拉一次 ReminderViewModel，触发其 init 中的轮询循环
    val reminderVm: ReminderViewModel = koinInject()

    // R8：启动跨端数据同步（每 30s push dirty + pull 最新）
    val syncManager: SyncManager = koinInject()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        syncManager.start()
    }

    CompositionLocalProvider(LocalThemeMode provides themeMode) {
        LearnSparkTheme(themeMode = themeMode) {
            // R8 修复：用主题背景色填充整个窗口，确保 PC 端主题一致
            // （之前没有 fillMaxSize + background，导致窗口边缘露出系统默认色）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background),
            ) {
                // 阶段 3.3：主题切换动画（200ms 淡入淡出）
                ThemeTransition(themeMode = themeMode) {
                    ResponsiveAppLayout()
                }
            }
        }
    }
}

expect fun currentPlatform(): String
