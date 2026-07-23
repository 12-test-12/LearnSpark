package com.learnspark.features.tasks

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.learnspark.data.model.TaskArticleLinkDto
import com.learnspark.data.model.TaskUploadDto
import com.learnspark.features.viewer.FileViewerScreen
import com.learnspark.features.viewer.FileViewerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * R4c + R5e：任务详情页（学习路线 → 阶段 → 任务）。
 *
 * 三段式：
 *   1. 任务资料：上传 / 列出文件（自动解析入库）→ 点击可查看（双端互通）
 *   2. AI 标注：触发 AI 扫描 → 列出可参考知识库文章
 *   3. 任务信息：标题 / 描述（简化展示）
 *
 * R5e：
 *   - 10s 轮询保证另一端上传文件即时同步显示
 *   - 点击已上传文件跳转到 FileViewerScreen
 *
 * 进入方式：传入 taskId（构造函数参数）。
 */
class TaskDetailScreen(
    private val taskId: String,
) : Screen {
    @Composable
    override fun Content() {
        val vm: TaskDetailViewModel = org.koin.compose.koinInject()
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(taskId) {
            vm.init(taskId)
            // R5e：10s 轮询，保证另一端上传的文件能即时同步显示
            while (isActive) {
                delay(10_000)
                vm.refresh(taskId)
            }
        }
        val uploads by vm.uploads.collectAsState()
        val links by vm.articleLinks.collectAsState()
        val ui by vm.ui.collectAsState()
        var showProvider by remember { mutableStateOf(false) }
        var selectedProvider by remember { mutableStateOf("默认") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("任务详情", style = MaterialTheme.typography.h6)
                            Text("ID: ${taskId.take(8)}…", style = MaterialTheme.typography.caption)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refresh(taskId) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ui.error?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                ui.suggestResult?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colors.primary,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(msg, color = MaterialTheme.colors.onPrimary, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.dismissSuggest() }) { Text("好") }
                        }
                    }
                }
                if (ui.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("加载中…", style = MaterialTheme.typography.caption)
                    }
                }
                if (ui.uploading) {
                    Text("正在上传…", style = MaterialTheme.typography.caption)
                    LinearProgressIndicator()
                }
                if (ui.suggesting) {
                    Text("AI 正在扫描知识库…", style = MaterialTheme.typography.caption)
                    LinearProgressIndicator()
                }

                // === 1. 任务资料 ===
                Text("📎 任务资料（${uploads.size}）", style = MaterialTheme.typography.subtitle1)
                Button(
                    onClick = { vm.pickAndUpload(allowedUploadExt) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ui.uploading,
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("上传文件（PDF / 笔记 / Evernote / 压缩包）")
                }
                if (uploads.isEmpty()) {
                    Text(
                        "支持 .pdf .docx .pptx .md .html .enex .zip 等\nPC 端上传的文件会通过 10s 轮询即时出现在这里。",
                        style = MaterialTheme.typography.caption,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(uploads, key = { it.id }) { u ->
                            UploadRow(
                                u = u,
                                onOpen = {
                                    navigator.push(
                                        FileViewerScreen(
                                            source = FileViewerViewModel.Source.TaskUpload(
                                                taskId = u.taskId,
                                                uploadId = u.id,
                                                fileName = u.fileName,
                                                fileType = u.fileType,
                                            ),
                                            fileName = u.fileName,
                                        )
                                    )
                                },
                                onDelete = { vm.deleteUpload(u.id) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // === 2. AI 标注 ===
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖 AI 标注可参考文章（${links.size}）", style = MaterialTheme.typography.subtitle1, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showProvider = true }) { Text("使用: $selectedProvider") }
                }
                Button(
                    onClick = {
                        val p = if (selectedProvider == "默认") null else selectedProvider
                        vm.suggestArticles(p)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ui.suggesting,
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (ui.suggesting) "AI 扫描中…" else "AI 一键扫描知识库")
                }
                if (links.isEmpty()) {
                    Text(
                        "AI 会扫描你的整个知识库，挑出最相关 1-8 篇可参考文章。\n" +
                            "前提：知识库中已有内容；并已在「设置 → AI 服务」配置好 provider。",
                        style = MaterialTheme.typography.caption,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(links, key = { it.entryId }) { l -> ArticleLinkRow(l, onRemove = { vm.removeArticleLink(l.entryId) }) }
                    }
                }
            }
        }

        if (showProvider) {
            AlertDialog(
                onDismissRequest = { showProvider = false },
                title = { Text("选择 AI 服务") },
                text = {
                    Column {
                        listOf("默认", "deepseek", "openai", "qwen", "glm", "moonshot", "custom").forEach { p ->
                            TextButton(
                                onClick = {
                                    selectedProvider = p
                                    showProvider = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (p == selectedProvider) "● $p" else "○ $p") }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showProvider = false }) { Text("关闭") } },
            )
        }
    }
}

@Composable
private fun UploadRow(u: TaskUploadDto, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Description, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(u.fileName, style = MaterialTheme.typography.body2, maxLines = 1)
                Text(
                    "${u.fileType} · ${humanSize(u.fileSize)} · ${u.uploadStatus}",
                    style = MaterialTheme.typography.caption,
                )
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Default.OpenInNew, contentDescription = "查看")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun ArticleLinkRow(l: TaskArticleLinkDto, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("相关度 ${l.relevance}%", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary)
                    Spacer(Modifier.size(4.dp))
                    Text("（${if (l.source == "ai") "AI 建议" else "手动"}）", style = MaterialTheme.typography.caption)
                }
                Text(l.reason, style = MaterialTheme.typography.body2)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "移除")
            }
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
    else -> "${bytes / 1024 / 1024 / 1024} GB"
}
