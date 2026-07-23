package com.learnspark.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.learnspark.shared.theme.LearnSparkColors
import java.time.LocalDate

/**
 * 学习日历热力图（GitHub 风格）。
 *
 * - 12 周 × 7 天 = 84 个小格子
 * - 颜色按当天的任务完成度分 5 档（0 / 低 / 中 / 高 / 满）
 * - 右上角带"少 / 多"图例
 */
@Composable
fun StudyCalendar(
    dailyProgress: List<DailyProgress>,
    weeks: Int = 12,
    modifier: Modifier = Modifier,
) {
    val cells = dailyProgress.takeLast(weeks * 7)
    val weeksData = cells.chunked(7)

    Column(modifier = modifier) {
        // 标题 + 90 天
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("📅", style = MaterialTheme.typography.caption)
            }
            Spacer(Modifier.width(8.dp))
            Text("学习日历", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Text("近 ${weeksData.size * 7} 天", style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(8.dp))
        // 网格
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // 星期标签（一 / 二 / 三 / 四 / 五）
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(top = 0.dp),
            ) {
                listOf("一", "", "三", "", "五").forEach { label ->
                    Box(modifier = Modifier.size(12.dp), contentAlignment = Alignment.CenterStart) {
                        if (label.isNotEmpty()) {
                            Text(
                                label,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            weeksData.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { day ->
                        HeatCell(day = day)
                    }
                    // 补齐空周（首周可能不到 7 天）
                    repeat(7 - week.size) {
                        Box(modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // 图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "少",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
            )
            Spacer(Modifier.width(4.dp))
            listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { level ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(HeatColor.forLevel(level)),
                )
                Spacer(Modifier.width(2.dp))
            }
            Spacer(Modifier.width(2.dp))
            Text(
                "多",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun HeatCell(day: DailyProgress) {
    val level = when {
        day.total == 0 -> 0f
        day.completed >= day.total -> 1f
        else -> day.completed.toFloat() / day.total.toFloat()
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(HeatColor.forLevel(level)),
    )
}

object HeatColor {
    private val empty = Color(0x33FFFFFF)
    private val level1 = LearnSparkColors.Primary.copy(alpha = 0.25f)
    private val level2 = LearnSparkColors.Primary.copy(alpha = 0.45f)
    private val level3 = LearnSparkColors.Primary.copy(alpha = 0.7f)
    private val level4 = LearnSparkColors.Primary

    fun forLevel(level: Float): Color = when {
        level <= 0f -> empty
        level < 0.25f -> level1
        level < 0.5f -> level2
        level < 0.85f -> level3
        else -> level4
    }
}

data class DailyProgress(
    val date: LocalDate,
    val total: Int,
    val completed: Int,
)

/** 12 周 × 7 天示例数据。 */
fun demoCalendar12Weeks(): List<DailyProgress> {
    val today = LocalDate.now()
    val totalDays = 12 * 7
    return (totalDays - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val total = (2..7).random()
        val completed = (0..total).random()
        DailyProgress(date = date, total = total, completed = completed)
    }
}

/** 单周 7 天示例数据（保留旧 WeeklyHeatmap 兼容）。 */
fun demoWeeklyHeatmap(): List<DailyProgress> {
    val today = LocalDate.now()
    return (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val total = (3..8).random()
        val completed = (0..total).random()
        DailyProgress(date = date, total = total, completed = completed)
    }
}

@Composable
fun WeeklyHeatmap(
    dailyProgress: List<DailyProgress>,
    modifier: Modifier = Modifier,
) {
    StudyCalendar(dailyProgress = dailyProgress, weeks = 1, modifier = modifier)
}
