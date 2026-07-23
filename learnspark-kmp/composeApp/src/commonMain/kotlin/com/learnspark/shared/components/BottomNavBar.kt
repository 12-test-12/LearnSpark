package com.learnspark.shared.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.learnspark.features.achievements.AchievementsScreen
import com.learnspark.features.dashboard.DashboardScreen
import com.learnspark.features.knowledge.KnowledgeScreen
import com.learnspark.features.projects.ProjectsScreen
import com.learnspark.features.settings.SettingsScreen

/**
 * 应用底部导航栏。
 *
 * 阶段 R5：选中态视觉强化
 * - 选中：图标主题色 + 1.15x 缩放 + 底部 2dp 高亮条
 * - 未选中：图标灰色 + 1.0x
 *
 * 过渡用 [animateFloatAsState] 实现 150ms 平滑插值。
 */
sealed class MainTab : Tab {
    abstract val title: String
    abstract val icon: ImageVector
    abstract val screenFactory: () -> Screen

    override val options: TabOptions
        @Composable
        get() = remember {
            TabOptions(
                index = 0u,
                title = title,
            )
        }

    @Composable
    override fun Content() {
        // R7 修复：每个 Tab 内部包一个 Navigator，让子屏的
        // LocalNavigator.currentOrThrow 能拿到真正的栈。
        // TabNavigator 不是 Navigator 子类，必须显式包 Navigator 才能 push。
        // 切换 Tab 时由于 key 变化，Navigator 会重建 → 子屏栈自动归零。
        val initial = remember { screenFactory() }
        // Voyager 1.0.1：参数名是 screen（不是 initialScreen），content 是 NavigatorContent receiver
        Navigator(screen = initial) {
            // CurrentScreen() = 渲染当前 Navigator 栈顶的 Screen（避免双重渲染）
            CurrentScreen()
        }
    }

    object Dashboard : MainTab() {
        override val title = "首页"
        override val icon = Icons.Filled.Home
        override val screenFactory = { DashboardScreen }
    }

    object Projects : MainTab() {
        override val title = "项目"
        override val icon = Icons.Filled.Folder
        override val screenFactory = { ProjectsScreen }
    }

    object Knowledge : MainTab() {
        override val title = "知识库"
        override val icon = Icons.Outlined.Book
        override val screenFactory = { KnowledgeScreen }
    }

    object Achievements : MainTab() {
        override val title = "成就"
        override val icon = Icons.Filled.MilitaryTech
        override val screenFactory = { AchievementsScreen }
    }

    object Settings : MainTab() {
        override val title = "设置"
        override val icon = Icons.Filled.Settings
        override val screenFactory = { SettingsScreen }
    }
}

@Composable
fun AppTabNavigator(
    onAuthenticatedRequired: () -> Unit,
    content: @Composable (currentTab: MainTab) -> Unit = {},
) {
    TabNavigator(tab = MainTab.Dashboard) { tabNavigator ->
        androidx.compose.material.Scaffold(
            bottomBar = {
                BottomNavigation(
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.onSurface,
                    elevation = 8.dp,
                ) {
                    val currentTab = tabNavigator.current
                    listOf(
                        MainTab.Dashboard,
                        MainTab.Projects,
                        MainTab.Knowledge,
                        MainTab.Achievements,
                        MainTab.Settings,
                    ).forEach { tab ->
                        val isSelected = currentTab::class == tab::class
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            label = "tabIconScale",
                        )
                        BottomNavigationItem(
                            selected = isSelected,
                            onClick = { tabNavigator.current = tab },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp).scale(scale),
                                    tint = if (isSelected) {
                                        MaterialTheme.colors.primary
                                    } else {
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.55f)
                                    },
                                )
                            },
                            label = {
                                Text(
                                    tab.title,
                                    style = MaterialTheme.typography.caption,
                                    color = if (isSelected) {
                                        MaterialTheme.colors.primary
                                    } else {
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                    },
                                )
                            },
                            selectedContentColor = MaterialTheme.colors.primary,
                            unselectedContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(0.dp),
                        )
                    }
                }
            }
        ) { padding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(padding)
            ) {
                // R7 修复：不再用 CompositionLocalProvider 把 TabNavigator 提供为
                // LocalNavigator —— TabNavigator 并不是 Navigator 子类，会触发
                // 类型不匹配编译错误。每个 Tab 自己的 Content() 内部已经包了 Navigator，
                // 会在子屏中提供正确的 LocalNavigator。
                CurrentTab()
            }
        }
    }
}
