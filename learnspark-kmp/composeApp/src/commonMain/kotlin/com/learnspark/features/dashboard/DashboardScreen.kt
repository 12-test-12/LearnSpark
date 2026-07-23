package com.learnspark.features.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.learnspark.shared.components.AvatarChip
import com.learnspark.shared.components.DashboardViewModel
import com.learnspark.shared.components.InsightBanner
import com.learnspark.shared.components.MetricTile
import com.learnspark.shared.components.ScreenTopBar
import com.learnspark.shared.components.SectionHeader
import com.learnspark.shared.components.StatCard
import com.learnspark.shared.components.StudyCalendar
import com.learnspark.shared.components.demoCalendar12Weeks
import com.learnspark.shared.theme.LearnSparkColors
import com.learnspark.shared.theme.LocalIsDarkTheme
import com.learnspark.features.knowledge.KnowledgeScreen
import org.koin.compose.koinInject

/**
 * 首页（仪表盘）。
 *
 * 视觉参考：截图里的深色风格仪表盘。
 * - 顶部栏：菜单 + 标题 + 桌面切换 + 头像
 * - 每日灵感卡（双引号 + 文案 + 来源）
 * - 3 个统计块（待办任务 / 连续打卡 / 总积分）
 * - 待办任务列表
 * - 12 周学习日历
 * - 知识库入口
 */
object DashboardScreen : Screen {
    private fun readResolve(): Any = DashboardScreen

    @Composable
    override fun Content() {
        val vm: DashboardViewModel = koinInject()
        val state by vm.state.collectAsState()
        val navigator = runCatching { LocalNavigator.currentOrThrow }.getOrNull()

        LaunchedEffect(Unit) { vm.load() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenTopBar(
                title = "仪表盘",
                onAvatarClick = { /* TODO: 打开用户中心 */ },
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ===== 1. 每日灵感 =====
                InsightBanner(
                    text = state.dailyQuote.text,
                    source = state.dailyQuote.source,
                    onAction = { vm.refreshQuote() },
                )

                // ===== 2. 三个统计块 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.CalendarToday,
                        iconTint = MaterialTheme.colors.primary,
                        iconBackground = MaterialTheme.colors.primary.copy(alpha = 0.18f),
                        label = "待办任务",
                        value = "${state.pendingTaskCount}",
                        suffix = "项",
                        onClick = { /* TODO: 跳转任务页 */ },
                    )
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.LocalFireDepartment,
                        iconTint = LearnSparkColors.Warning,
                        iconBackground = LearnSparkColors.Warning.copy(alpha = 0.18f),
                        label = "连续打卡",
                        value = "${state.streakDays}",
                        suffix = "天",
                        caption = if (state.streakDays == 0) "开启第一天" else null,
                    )
                    MetricTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.EmojiEvents,
                        iconTint = LearnSparkColors.Info,
                        iconBackground = LearnSparkColors.Info.copy(alpha = 0.18f),
                        label = "总积分",
                        value = "${state.totalPoints}",
                    )
                }

                // ===== 3. 待办任务列表 =====
                StatCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SectionHeader(
                            icon = Icons.Filled.CalendarToday,
                            title = "待办任务",
                            badge = "${state.pendingTaskCount} / ${state.totalTaskCount}",
                        )
                        Spacer(Modifier.height(8.dp))
                        if (state.todayTasks.isEmpty()) {
                            Text(
                                "今天还没有任务 — 打开项目添加你的第一个任务",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            state.todayTasks.take(3).forEach { task ->
                                TaskRow(
                                    title = task.title,
                                    subtitle = task.project,
                                    status = task.statusLabel,
                                    onStart = { /* TODO: 启动番茄钟 */ },
                                )
                            }
                        }
                    }
                }

                // ===== 4. 学习日历 =====
                StatCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        StudyCalendar(
                            dailyProgress = demoCalendar12Weeks(),
                            weeks = 12,
                        )
                    }
                }

                // ===== 5. 知识库入口 =====
                KnowledgeEntryCard(
                    count = state.knowledgeEntryCount,
                    onClick = { navigator?.push(KnowledgeScreen) },
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TaskRow(
    title: String,
    subtitle: String?,
    status: String,
    onStart: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colors.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                status,
                color = MaterialTheme.colors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colors.primary)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "开始",
                tint = MaterialTheme.colors.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun KnowledgeEntryCard(
    count: Int,
    onClick: () -> Unit,
) {
    StatCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LearnSparkColors.Info.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("📚", fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$count 条",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "已沉淀知识",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            }
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = "进入",
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}
