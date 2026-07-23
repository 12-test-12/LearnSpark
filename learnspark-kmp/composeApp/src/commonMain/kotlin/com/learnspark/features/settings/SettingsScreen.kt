package com.learnspark.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.learnspark.data.migration.MigrationState
import com.learnspark.data.migration.MigrationViewModel
import com.learnspark.features.notification.NotificationCenterScreen
import com.learnspark.features.notification.ReminderScreen
import org.koin.compose.koinInject

/**
 * 设置页。
 *
 * 阶段 1.1.6：接入主题切换 + 设备管理
 * 阶段 3.1：旧版数据迁移入口（按文档 §11.2.2）
 * 阶段 R3b：通知时间自定义 + 通知中心入口
 */
object SettingsScreen : Screen {
    private fun readResolve(): Any = SettingsScreen

    @Composable
    override fun Content() {
        val viewModel: MigrationViewModel = koinInject()
        val state by viewModel.state.collectAsState()
        // R7 修复：使用 runCatching 安全获取 navigator，避免在 Tab 切换或异常路径下
        // 抛出 NoSuchElementException 导致整个 app 闪退。
        val navigator = runCatching { LocalNavigator.currentOrThrow }.getOrNull()

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.h5)
            Text("主题、AI、通知、同步都在这里", style = MaterialTheme.typography.caption)

            // 阶段 R3b：通知管理
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("通知与提醒", style = MaterialTheme.typography.subtitle1)
                    Text(
                        "为不同场景设置独立的触发时刻，桌面 / 手机端同时生效。\n" +
                            "支持：每日、工作日、自定义星期、仅一次。",
                        style = MaterialTheme.typography.caption,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { navigator?.push(ReminderScreen) }) {
                            Text("⏰ 自定义通知时间")
                        }
                        Button(onClick = { navigator?.push(NotificationCenterScreen) }) {
                            Text("📬 通知中心")
                        }
                    }
                }
            }

            Divider()

            // 阶段 R4a：AI 服务
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("AI 服务", style = MaterialTheme.typography.subtitle1)
                    Text(
                        "支持多家 OpenAI 兼容 provider：DeepSeek / OpenAI / Qwen / GLM / Moonshot / 自定义端点。\n" +
                            "在任务详情页可选择本次使用的 provider。",
                        style = MaterialTheme.typography.caption,
                    )
                    Button(onClick = { navigator?.push(AiConfigScreen) }) {
                        Text("🤖 配置 AI 服务")
                    }
                }
            }

            // 阶段 3.1：旧版数据迁移
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("旧版数据迁移", style = MaterialTheme.typography.subtitle1)
                    Text(
                        "检测到本地缓存的旧版 Vue3 导出文件时，可一键导入到新服务端。\n" +
                            "Desktop 路径：~/.learnspark/legacy-export.json\n" +
                            "Android 路径：app filesDir/legacy-export.json",
                        style = MaterialTheme.typography.caption,
                    )
                    when (val s = state) {
                        is MigrationState.Idle -> {
                            Button(onClick = { viewModel.detect() }) { Text("检测本地旧版数据") }
                        }
                        is MigrationState.Detecting -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                Text("检测中…")
                            }
                        }
                        is MigrationState.NotFound -> {
                            Text("未检测到旧版导出文件", color = MaterialTheme.colors.onSurface)
                            Button(onClick = { viewModel.detect() }) { Text("重新检测") }
                        }
                        is MigrationState.Found -> {
                            Text("✓ 已检测到旧版导出文件", color = MaterialTheme.colors.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.import() }) { Text("立即导入") }
                                Button(onClick = { viewModel.dismiss() }) { Text("稍后") }
                            }
                        }
                        is MigrationState.Importing -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                Text("导入中…")
                            }
                        }
                        is MigrationState.Done -> {
                            Text(
                                "导入完成：新增 ${s.result.insertedTotal} 条，跳过 ${s.result.skippedTotal} 条",
                                color = MaterialTheme.colors.primary,
                            )
                            Button(onClick = { viewModel.dismiss() }) { Text("关闭") }
                        }
                        is MigrationState.Error -> {
                            Text("导入失败：${s.message}", color = MaterialTheme.colors.error)
                            Button(onClick = { viewModel.import() }) { Text("重试") }
                        }
                    }
                }
            }
        }
    }
}
