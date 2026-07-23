package com.learnspark.features.achievements

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.learnspark.features.gamification.BUILT_IN_ACHIEVEMENTS
import com.learnspark.features.gamification.GamificationService
import com.learnspark.features.gamification.PointAccount

/**
 * 阶段 3.3：成就页。
 *
 * 展示：
 * - 积分账户（总积分 / 等级 / 距离下一级进度条）
 * - 当前连续打卡（current / longest）
 * - 徽章网格（已解锁 vs 未解锁）
 */
object AchievementsScreen : Screen {
    private fun readResolve(): Any = AchievementsScreen

    @Composable
    override fun Content() {
        // Demo 用：每次进入页面重新创建 service，简化状态管理
        val service = remember { GamificationService() }
        val account: PointAccount = remember { service.account() }
        val streak = remember { service.currentStreak() }
        val unlockedIds = remember { service.unlockedBadges().map { it.achievementId }.toSet() }

        // 演示：触发一次"初次打卡"事件，使徽章有数据展示
        remember {
            service.processEvent("check_in", com.learnspark.features.gamification.GamificationContext())
            Unit
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 顶部：账户信息
            AccountCard(account = account, streak = streak.currentDays, longestStreak = streak.longestDays)

            // 徽章网格
            Text("徽章 (${unlockedIds.size}/${BUILT_IN_ACHIEVEMENTS.size})", style = MaterialTheme.typography.h6)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(BUILT_IN_ACHIEVEMENTS) { achievement ->
                    BadgeCard(
                        icon = achievement.icon,
                        name = achievement.name,
                        description = achievement.description,
                        points = achievement.points,
                        unlocked = achievement.id in unlockedIds,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: PointAccount, streak: Int, longestStreak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Lv.${account.level}", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
                    Text("总积分 ${account.total}", style = MaterialTheme.typography.caption)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("🔥 $streak 天", style = MaterialTheme.typography.h5)
                    Text("最长 ${longestStreak} 天", style = MaterialTheme.typography.caption)
                }
            }
            Spacer(Modifier.height(4.dp))
            // 修复：进度条百分比与文案一致；总积分 0 时显示 0%，不显示"满的"进度
            val progress = (account.total % 100) / 100f
            val currentLevelProgress = account.total % 100
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Text(
                if (account.nextLevelPoints > 0) {
                    "距离下一级还差 ${account.nextLevelPoints} 分（当前 $currentLevelProgress / 100）"
                } else {
                    "已是最高等级"
                },
                style = MaterialTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun BadgeCard(
    icon: String,
    name: String,
    description: String,
    points: Int,
    unlocked: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (unlocked) 1f else 0.35f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (unlocked) MaterialTheme.colors.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 28.sp)
            }
            Text(
                name,
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                description,
                style = MaterialTheme.typography.caption,
                textAlign = TextAlign.Center,
            )
            if (unlocked) {
                Text("+${points} 分", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary)
            } else {
                Text("未解锁", style = MaterialTheme.typography.caption)
            }
        }
    }
}
