package com.learnspark.shared.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabNavigator

/**
 * 响应式应用布局。
 *
 * 通过 [BoxWithConstraints] 获取当前容器的宽度（单位 dp）：
 * - 宽度 ≥ 840dp（典型 Desktop / 平板横屏）：左侧 NavigationRail + 右侧内容
 * - 宽度 < 840dp（手机竖屏）：底部 BottomNavigation + 全屏内容
 *
 * 两种模式共用同一个 TabNavigator 状态，确保切换宽度时仍保留当前选中的 Tab。
 */
@Composable
fun ResponsiveAppLayout() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            DesktopLayout()
        } else {
            MobileLayout()
        }
    }
}

@Composable
private fun DesktopLayout() {
    TabNavigator(tab = MainTab.Dashboard) { tabNavigator ->
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight().width(96.dp),
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
                    NavigationRailItem(
                        selected = currentTab::class == tab::class,
                        onClick = { tabNavigator.current = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, style = MaterialTheme.typography.caption) },
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(start = 8.dp),
            ) {
                // R7 修复：每个 Tab 内部包一个 Navigator（与 BottomNavBar 一致）
                CurrentTab()
            }
        }
    }
}

@Composable
private fun MobileLayout() {
    AppTabNavigator(onAuthenticatedRequired = {})
}
