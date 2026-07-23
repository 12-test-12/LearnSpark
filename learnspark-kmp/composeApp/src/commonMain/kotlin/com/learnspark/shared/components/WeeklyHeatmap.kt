package com.learnspark.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * 阶段 3.3：7 日热力图（按文档 §3.3 仪表盘）。
 *
 * 简化版：7 列 × 1 行（按周内日），颜色按当周每日任务完成度（0~1）。
 * 真实数据：每日的「完成 / 总数」。
 */
@Composable
fun WeeklyHeatmap(
    dailyProgress: List<DailyProgress>,  // 长度 = 7
    modifier: Modifier = Modifier,
) {
    require(dailyProgress.size == 7) { "WeeklyHeatmap requires 7 days, got ${dailyProgress.size}" }

    Column(modifier = modifier) {
        Text("7 日热力图", style = MaterialTheme.typography.subtitle1)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dailyProgress.forEach { day ->
                HeatmapCell(day = day)
            }
        }
    }
}

@Composable
private fun HeatmapCell(day: DailyProgress) {
    val color = when {
        day.completed == 0 -> MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
        day.completed >= day.total -> MaterialTheme.colors.primary
        day.completed.toFloat() / day.total.toFloat() >= 0.5f ->
            MaterialTheme.colors.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colors.primary.copy(alpha = 0.3f)
    }
    val dow = day.date.dayOfWeek.value // 1..7
    val dowLabel = when (dow) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; 7 -> "日"
        else -> "?"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text("${day.completed}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onPrimary)
        }
        Text(dowLabel, style = MaterialTheme.typography.caption)
    }
}

data class DailyProgress(
    val date: LocalDate,
    val total: Int,
    val completed: Int,
)

/**
 * 阶段 3.3：示例数据生成器。
 * 用最近 7 天的随机数据演示，后续接入真实数据源。
 */
fun demoWeeklyHeatmap(): List<DailyProgress> {
    val today = LocalDate.now()
    return (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val total = (3..8).random()
        val completed = (0..total).random()
        DailyProgress(date = date, total = total, completed = completed)
    }
}
