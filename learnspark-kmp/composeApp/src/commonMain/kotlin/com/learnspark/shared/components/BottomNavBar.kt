package com.learnspark.shared.components

import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
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
 * - Android: 5 个 Tab + 底部 BottomNavigation
 * - Desktop: 侧边 NavigationRail（窗口宽 ≥ 840dp 时）或底部导航
 *
 * 由 [ResponsiveLayout] 决定显示形式。
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
        screenFactory().Content()
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
                ) {
                    val currentTab = tabNavigator.current
                    listOf(
                        MainTab.Dashboard,
                        MainTab.Projects,
                        MainTab.Knowledge,
                        MainTab.Achievements,
                        MainTab.Settings,
                    ).forEach { tab ->
                        BottomNavigationItem(
                            selected = currentTab::class == tab::class,
                            onClick = { tabNavigator.current = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, style = MaterialTheme.typography.caption) },
                            modifier = Modifier.padding(0.dp),
                        )
                    }
                }
            }
        ) { padding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(padding)
            ) {
                CurrentTab()
            }
        }
    }
}
