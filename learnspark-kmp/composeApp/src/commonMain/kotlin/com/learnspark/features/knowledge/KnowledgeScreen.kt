package com.learnspark.features.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.learnspark.data.model.KnowledgeEntryDto
import com.learnspark.data.model.KnowledgeFolderDto
import com.learnspark.features.viewer.FileViewerScreen
import com.learnspark.features.viewer.FileViewerViewModel
import org.koin.compose.koinInject

/**
 * R3a + R3c + R5e：知识库 Screen（文件夹管理 + 条目列表 + AI 整理）。
 *
 * 双端通用（Compose Multiplatform）。移动端用 BottomNavBar 已可显示，
 * 桌面端用侧边栏（由 ResponsiveAppLayout 适配）。
 *
 * R5e：进入文件夹后展示其下条目，点击跳转到 FileViewerScreen。
 *      通过 ViewModel 10s 轮询保证双端上传文件即时同步。
 */
object KnowledgeScreen : Screen {
    private fun readResolve(): Any = KnowledgeScreen

    @Composable
    override fun Content() {
        val vm: KnowledgeViewModel = koinInject()
        val navigator = LocalNavigator.currentOrThrow
        val folders by vm.folders.collectAsState()
        val entries by vm.entries.collectAsState()
        val ui by vm.ui.collectAsState()
        var selectedFolder by remember { mutableStateOf<KnowledgeFolderDto?>(null) }
        var showCreate by remember { mutableStateOf(false) }
        var renameTarget by remember { mutableStateOf<KnowledgeFolderDto?>(null) }
        var showOrganize by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (selectedFolder == null) "知识库" else "📁 ${selectedFolder!!.name}",
                                style = MaterialTheme.typography.h6,
                            )
                            Text(
                                if (selectedFolder == null) {
                                    "${folders.size} 文件夹 · ${entries.size} 条目"
                                } else {
                                    "父：${selectedFolder!!.path}"
                                },
                                style = MaterialTheme.typography.caption,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Default.Add, contentDescription = "新建文件夹")
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
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), backgroundColor = MaterialTheme.colors.error) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(msg, color = MaterialTheme.colors.onError, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.dismissError() }) { Text("关闭") }
                        }
                    }
                }

                // 面包屑导航
                if (selectedFolder != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedFolder = null }) { Text("← 根目录") }
                    }
                }

                if (selectedFolder == null) {
                    // 根目录视图：显示文件夹 + 所有未分类条目
                    val rootFolders = folders.filter { it.parentId == null }
                        .sortedWith(compareBy({ it.sortOrder }, { it.name }))
                    val unfiled = entries.filter { it.folderId == null }

                    if (rootFolders.isEmpty() && unfiled.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colors.primary,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("暂无内容", style = MaterialTheme.typography.body1)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { showCreate = true }) { Text("+ 新建文件夹") }
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { showOrganize = true }) {
                                    Text("🤖 AI 一键整理")
                                }
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (rootFolders.isNotEmpty()) {
                                item {
                                    Text(
                                        "文件夹（${rootFolders.size}）",
                                        style = MaterialTheme.typography.subtitle2,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                                items(rootFolders, key = { "f-${it.id}" }) { folder ->
                                    FolderRow(
                                        folder = folder,
                                        onOpen = { selectedFolder = folder },
                                        onRename = { renameTarget = folder },
                                        onDelete = { vm.deleteFolder(folder.id) },
                                    )
                                }
                            }
                            if (unfiled.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "未分类文件（${unfiled.size}）",
                                        style = MaterialTheme.typography.subtitle2,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                                items(unfiled, key = { "e-${it.id}" }) { entry ->
                                    EntryRow(
                                        entry = entry,
                                        onOpen = {
                                            navigator.push(
                                                FileViewerScreen(
                                                    source = FileViewerViewModel.Source.Knowledge(
                                                        entryId = entry.id,
                                                        fileName = entry.fileName,
                                                        fileType = entry.fileType,
                                                    ),
                                                    fileName = entry.fileName,
                                                )
                                            )
                                        },
                                        onDelete = { vm.deleteEntry(entry.id) },
                                    )
                                }
                            }
                            item {
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { showOrganize = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("🤖 AI 一键整理（建议目标文件夹）") }
                            }
                        }
                    }
                } else {
                    // 文件夹视图：显示子文件夹 + 当前文件夹下的条目
                    val childFolders = folders.filter { it.parentId == selectedFolder!!.id }
                        .sortedWith(compareBy({ it.sortOrder }, { it.name }))
                    val childEntries = entries.filter { it.folderId == selectedFolder!!.id }
                    if (childFolders.isEmpty() && childEntries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("空文件夹", style = MaterialTheme.typography.body1)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "从 PC 端或移动端上传文件后，会在此自动出现（10s 轮询）。",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (childFolders.isNotEmpty()) {
                                items(childFolders, key = { "f-${it.id}" }) { folder ->
                                    FolderRow(
                                        folder = folder,
                                        onOpen = { selectedFolder = folder },
                                        onRename = { renameTarget = folder },
                                        onDelete = { vm.deleteFolder(folder.id) },
                                    )
                                }
                            }
                            if (childEntries.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "文件（${childEntries.size}）",
                                        style = MaterialTheme.typography.subtitle2,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                                items(childEntries, key = { "e-${it.id}" }) { entry ->
                                    EntryRow(
                                        entry = entry,
                                        onOpen = {
                                            navigator.push(
                                                FileViewerScreen(
                                                    source = FileViewerViewModel.Source.Knowledge(
                                                        entryId = entry.id,
                                                        fileName = entry.fileName,
                                                        fileType = entry.fileType,
                                                    ),
                                                    fileName = entry.fileName,
                                                )
                                            )
                                        },
                                        onDelete = { vm.deleteEntry(entry.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCreate) {
            CreateFolderDialog(
                parent = selectedFolder,
                onDismiss = { showCreate = false },
                onCreate = { name, _ ->
                    vm.createFolder(name, selectedFolder?.id)
                    showCreate = false
                },
            )
        }
        renameTarget?.let { target ->
            RenameFolderDialog(
                target = target,
                onDismiss = { renameTarget = null },
                onRename = { newName ->
                    vm.renameFolder(target.id, newName)
                    renameTarget = null
                },
            )
        }
        if (showOrganize) {
            OrganizeDialog(
                suggestions = ui.lastSuggestions,
                onSuggest = { vm.suggestOrganize() },
                onApply = { accepts -> vm.applyOrganize(accepts) },
                onDismiss = { showOrganize = false },
            )
        }
    }
}

@Composable
private fun FolderRow(
    folder: KnowledgeFolderDto,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        elevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = folder.color?.let { parseHexColor(it) } ?: MaterialTheme.colors.primary
            Box(
                modifier = Modifier.size(40.dp).background(tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(folder.icon ?: "📁", style = MaterialTheme.typography.h6)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Medium)
                Text(
                    "${folder.path} · 深度 ${folder.depth}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onRename) { Icon(Icons.Default.Edit, contentDescription = "重命名") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除") }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun EntryRow(
    entry: KnowledgeEntryDto,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        elevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colors.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.fileName, style = MaterialTheme.typography.subtitle2, maxLines = 1)
                Text(
                    "${entry.fileType.ifBlank { "?" }} · ${humanSize(entry.fileSize)} · ${entry.parseStatus}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onOpen) { Icon(Icons.Default.OpenInNew, contentDescription = "查看") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除") }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    parent: KnowledgeFolderDto?,
    onDismiss: () -> Unit,
    onCreate: (name: String, parent: KnowledgeFolderDto?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parent == null) "新建根目录文件夹" else "新建到 ${parent.name}") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("文件夹名") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, parent) },
                enabled = name.isNotBlank(),
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RenameFolderDialog(
    target: KnowledgeFolderDto,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(target.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("新名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onRename(name) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun OrganizeDialog(
    suggestions: List<KnowledgeViewModel.OrganizeSuggestion>,
    onSuggest: () -> Unit,
    onApply: (List<Triple<String, String?, String?>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🤖 AI 整理") },
        text = {
            Column {
                if (suggestions.isEmpty()) {
                    Text("点击「让 AI 建议」可对最近的知识条目自动分类到现有文件夹。")
                } else {
                    Text("AI 建议了 ${suggestions.size} 条，点击「应用选中」可批量落库。")
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(240.dp)) {
                        items(suggestions) { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = s.entryId in selected.value,
                                    onCheckedChange = { checked ->
                                        selected.value = if (checked) selected.value + s.entryId
                                        else selected.value - s.entryId
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (s.folderId == null) "（未分类）" else "→ 目录 ${s.folderId.take(8)}",
                                        style = MaterialTheme.typography.body2,
                                    )
                                    Text(
                                        s.reason,
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (suggestions.isEmpty()) {
                    TextButton(onClick = onSuggest) { Text("让 AI 建议") }
                } else {
                    TextButton(
                        onClick = {
                            onApply(
                                suggestions
                                    .filter { it.entryId in selected.value }
                                    .map { Triple(it.entryId, it.folderId, it.reason) }
                            )
                        },
                        enabled = selected.value.isNotEmpty(),
                    ) { Text("应用选中 (${selected.value.size})") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
    else -> "${bytes / 1024 / 1024 / 1024} GB"
}

/**
 * 解析 #RRGGBB / #AARRGGBB 形式的颜色字符串为 Compose [Color]。
 * 失败时回退到 null（让调用方决定默认值）。
 */
private fun parseHexColor(hex: String): Color? {
    val raw = hex.trim().removePrefix("#")
    if (raw.length != 6 && raw.length != 8) return null
    val value = raw.toLongOrNull(16) ?: return null
    return when (raw.length) {
        6 -> Color(red = ((value shr 16) and 0xFF).toInt() / 255f,
                   green = ((value shr 8) and 0xFF).toInt() / 255f,
                   blue = (value and 0xFF).toInt() / 255f,
                   alpha = 1f)
        8 -> Color(red = ((value shr 16) and 0xFF).toInt() / 255f,
                   green = ((value shr 8) and 0xFF).toInt() / 255f,
                   blue = (value and 0xFF).toInt() / 255f,
                   alpha = ((value shr 24) and 0xFF).toInt() / 255f)
        else -> null
    }
}
