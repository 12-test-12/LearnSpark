package com.learnspark.features.notification

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.learnspark.data.model.ReminderSettingDto
import org.koin.compose.koinInject

/**
 * R3b：通知时间自定义 Screen。
 *
 * - 列出当前用户全部 reminder settings
 * - 新建：标题 + 触发时间（HH:mm）+ 重复模式
 * - 启停切换、删除
 */
object ReminderScreen : Screen {
    private fun readResolve(): Any = ReminderScreen

    @Composable
    override fun Content() {
        val vm: ReminderViewModel = koinInject()
        val settings by vm.settings.collectAsState()
        val ui by vm.ui.collectAsState()
        var showCreate by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("通知与提醒") },
                    actions = {
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Default.Add, contentDescription = "新建提醒")
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                if (ui.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("同步中…", style = MaterialTheme.typography.caption)
                    }
                }
                ui.error?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        backgroundColor = MaterialTheme.colors.error,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(msg, color = MaterialTheme.colors.onError, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.dismissError() }) { Text("关闭") }
                        }
                    }
                }

                Text(
                    "为不同场景设置独立的触发时刻，桌面/手机端均可生效。\n" +
                        "支持：每日、工作日、每周、自定义星期、仅一次。",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(8.dp))

                if (settings.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("还没有提醒", style = MaterialTheme.typography.h6)
                        Spacer(Modifier.height(4.dp))
                        Text("点击右上角「+」创建你的第一个通知时间。", style = MaterialTheme.typography.caption)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showCreate = true }) { Text("+ 新建提醒") }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(settings, key = { it.id }) { setting ->
                            ReminderRow(
                                setting = setting,
                                onToggle = { vm.toggleEnabled(setting, it) },
                                onDelete = { vm.deleteSetting(setting) },
                            )
                        }
                    }
                }
            }
        }

        if (showCreate) {
            CreateReminderDialog(
                onDismiss = { showCreate = false },
                onCreate = { title, message, time, pattern, mask ->
                    vm.createSetting(title, message, time, pattern, mask, enabled = true)
                    showCreate = false
                },
            )
        }
    }
}

@Composable
private fun ReminderRow(
    setting: ReminderSettingDto,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(setting.title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Medium)
                val patternLabel = when (setting.repeatPattern.uppercase()) {
                    "DAILY" -> "每天"
                    "WEEKDAYS" -> "工作日"
                    "WEEKLY" -> "每周 ${weekdayMaskLabel(setting.weekdayMask)}"
                    "ONCE" -> "仅一次"
                    else -> setting.repeatPattern
                }
                Text(
                    "${setting.triggerTime.take(5)} · $patternLabel" +
                        (setting.nextFireAt?.let { " · 下次 $it" } ?: ""),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
                setting.message?.let {
                    Text(it, style = MaterialTheme.typography.caption, maxLines = 2)
                }
            }
            Switch(checked = setting.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除") }
        }
    }
}

private fun weekdayMaskLabel(mask: Int): String {
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    val bits = listOf(1, 2, 4, 8, 16, 32, 64)
    val picked = bits.zip(names).filter { (mask and it.first) != 0 }.map { it.second }
    return if (picked.size == 7) "每天" else picked.joinToString("、")
}

@Composable
private fun CreateReminderDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, message: String?, time: String, pattern: String, weekdayMask: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("09") }
    var minute by remember { mutableStateOf("00") }
    var pattern by remember { mutableStateOf("DAILY") }
    val weekdayChecks = remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true)
                OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("内容（可选）") }, singleLine = false, maxLines = 3)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("时") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp),
                    )
                    Text(" : ", style = MaterialTheme.typography.h6)
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("分") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp),
                    )
                }

                Text("重复", style = MaterialTheme.typography.caption)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PatternChip("DAILY", "每天", pattern) { pattern = it }
                    Spacer(Modifier.width(4.dp))
                    PatternChip("WEEKDAYS", "工作日", pattern) { pattern = it }
                    Spacer(Modifier.width(4.dp))
                    PatternChip("WEEKLY", "每周", pattern) { pattern = it }
                    Spacer(Modifier.width(4.dp))
                    PatternChip("ONCE", "仅一次", pattern) { pattern = it }
                }

                if (pattern == "WEEKLY") {
                    Text("选择星期", style = MaterialTheme.typography.caption)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val labels = listOf("一", "二", "三", "四", "五", "六", "日")
                        labels.forEachIndexed { idx, label ->
                            val bit = 1 shl idx
                            val checked = bit in weekdayChecks.value
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { c ->
                                        weekdayChecks.value = if (c) weekdayChecks.value + bit
                                        else weekdayChecks.value - bit
                                    },
                                )
                                Text(label, style = MaterialTheme.typography.caption)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && hour.isNotBlank() && minute.isNotBlank(),
                onClick = {
                    val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 9
                    val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    val mask = if (pattern == "WEEKLY") weekdayChecks.value.sum() else 127
                    onCreate(title, message.takeIf { it.isNotBlank() }, "%02d:%02d:00".format(h, m), pattern, mask)
                },
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PatternChip(label: String, text: String, current: String, onClick: (String) -> Unit) {
    val selected = label == current
    val bg = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.surface
    val fg = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface
    androidx.compose.material.Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(2.dp),
    ) {
        TextButton(onClick = { onClick(label) }) {
            Text(text, color = fg, style = MaterialTheme.typography.caption)
        }
    }
}
