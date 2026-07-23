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
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.learnspark.data.model.ReminderLogDto
import org.koin.compose.koinInject

/**
 * R3b：通知中心 Screen。
 *
 * - 显示未确认的 reminder_logs
 * - 点击「✓」确认单条；点击「全部已读」批量清空
 * - 每分钟 ViewModel 自动拉一次（poll loop）
 */
object NotificationCenterScreen : Screen {
    private fun readResolve(): Any = NotificationCenterScreen

    @Composable
    override fun Content() {
        val vm: ReminderViewModel = koinInject()
        val pending by vm.pendingLogs.collectAsState()
        val ui by vm.ui.collectAsState()
        // R7 修复：用 runCatching 包一层；返回按钮用 navigator?.pop()
        val navigator = runCatching { LocalNavigator.currentOrThrow }.getOrNull()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("通知中心", style = MaterialTheme.typography.h6)
                            Text(
                                if (ui.lastPollAt > 0) "最近轮询 ${(System.currentTimeMillis() - ui.lastPollAt) / 1000}s 前"
                                else "未连接",
                                style = MaterialTheme.typography.caption,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator?.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refreshLogs(showNotifications = false) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        TextButton(
                            onClick = { vm.acknowledgeAll() },
                            enabled = pending.isNotEmpty(),
                        ) { Text("全部已读") }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
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

                if (pending.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🎉", style = MaterialTheme.typography.h2)
                        Spacer(Modifier.height(8.dp))
                        Text("暂无未读通知", style = MaterialTheme.typography.h6)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "当提醒被触发时，会同时显示在桌面与手机端。",
                            style = MaterialTheme.typography.caption,
                        )
                    }
                } else {
                    Text("未读 ${pending.size} 条", style = MaterialTheme.typography.subtitle2)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(pending, key = { it.id }) { log -> LogRow(log, onAck = { vm.acknowledge(log) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: ReminderLogDto, onAck: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(log.title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Medium)
                log.message?.let { Text(it, style = MaterialTheme.typography.body2) }
                Text(
                    "触发于 ${log.firedAt}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onAck) {
                Icon(Icons.Default.Done, contentDescription = "标记已读", tint = MaterialTheme.colors.primary)
            }
        }
    }
}
