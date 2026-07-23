package com.learnspark.features.projects

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.learnspark.data.db.ProjectRepository
import com.learnspark.data.model.ProjectDto
import com.learnspark.features.settings.AiConfigScreen
import com.learnspark.shared.components.ActionButton
import com.learnspark.shared.components.CircularProgressRing
import com.learnspark.shared.components.ScreenTopBar
import com.learnspark.shared.components.SectionHeader
import com.learnspark.shared.components.StatCard
import com.learnspark.shared.theme.LearnSparkColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.koin.compose.koinInject

/**
 * 项目详情 / 学习路线页。
 *
 * 视觉参考：截图里的"项目详情"页面：
 * - 顶部：返回按钮 + 编辑项目 + AI 生成路线 + 添加任务（蓝/绿/绿）
 * - 进度卡：项目进度环 + 等级徽章 + 4 项指标（总任务/待提交/已通过/每日时长）
 * - 学习路线：未分组任务 / 阶段分组
 *
 * 现阶段使用本地缓存项目（[ProjectRepository]）作为数据源，
 * 任务数据从项目 metadata 派生（每个项目 → 1 个代表任务）。
 */
object ProjectsScreen : Screen {
    private fun readResolve(): Any = ProjectsScreen

    @Composable
    override fun Content() {
        val repository: ProjectRepository = koinInject()
        val navigator = runCatching { LocalNavigator.currentOrThrow }.getOrNull()
        val scope = rememberCoroutineScope()

        val state = remember { MutableStateFlow(ProjectsUiState()) }
        val ui by state.collectAsState()
        var showCreate by remember { mutableStateOf(false) }
        var showEdit by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            // 监听项目变化
            repository.observeActiveProjects().collect { list ->
                val picked = list.firstOrNull()
                state.update { it.copy(project = picked, allProjects = list) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .verticalScroll(rememberScrollState()),
        ) {
            // ===== 顶部栏 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Book,
                    contentDescription = "项目",
                    tint = MaterialTheme.colors.onSurface,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "项目详情",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ===== 顶部操作行 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionButton(
                        text = "返回项目列表",
                        onClick = { navigator?.pop() },
                        background = MaterialTheme.colors.surface,
                        foreground = MaterialTheme.colors.onSurface,
                        leadingIcon = Icons.Filled.Book,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "编辑项目",
                        onClick = { if (ui.project != null) showEdit = true },
                        background = MaterialTheme.colors.surface,
                        foreground = MaterialTheme.colors.onSurface,
                        leadingIcon = Icons.Filled.Edit,
                    )
                    ActionButton(
                        text = "AI 生成路线",
                        onClick = { navigator?.push(AiConfigScreen) },
                        background = LearnSparkColors.Info,
                        foreground = MaterialTheme.colors.onPrimary,
                        leadingIcon = Icons.Filled.AutoAwesome,
                    )
                }
                ActionButton(
                    text = "添加任务",
                    onClick = { showCreate = true },
                    background = MaterialTheme.colors.primary,
                    foreground = MaterialTheme.colors.onPrimary,
                    leadingIcon = Icons.Filled.Add,
                )

                // ===== 项目进度卡 =====
                ui.project?.let { p ->
                    ProjectProgressCard(project = p)
                } ?: EmptyProjectCard(onCreate = { showCreate = true })

                // ===== 学习路线 =====
                StatCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("学习路线", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))

                        // 未分组
                        GroupHeader(name = "未分组任务", count = 1, progress = 0f)
                        Spacer(Modifier.height(6.dp))
                        TaskGroup(
                            name = "未分组",
                            icon = Icons.Filled.Flag,
                            count = 1,
                            progress = 0f,
                            tasks = listOf(
                                TaskUi("示例任务 1", 1, "待提交"),
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }

        if (showCreate) {
            CreateProjectDialog(
                onDismiss = { showCreate = false },
                onCreate = { name, goal ->
                    scope.launch {
                        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
                        repository.upsert(
                            ProjectDto(
                                id = java.util.UUID.randomUUID().toString(),
                                name = name,
                                goal = goal,
                                createdAt = now,
                                updatedAt = now,
                            )
                        )
                    }
                    showCreate = false
                },
            )
        }
        if (showEdit && ui.project != null) {
            val p = ui.project!!
            EditProjectDialog(
                initialName = p.name,
                initialGoal = p.goal ?: "",
                onDismiss = { showEdit = false },
                onSave = { name, goal ->
                    scope.launch {
                        repository.upsert(
                            p.copy(name = name, goal = goal, updatedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString())
                        )
                    }
                    showEdit = false
                },
            )
        }
    }
}

@Composable
private fun ProjectProgressCard(project: ProjectDto) {
    StatCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 顶部薄进度条
            LinearProgressIndicator(
                progress = 1f,  // 截图是满的（演示色带）
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colors.primary,
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "1",
                            style = MaterialTheme.typography.h4,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colors.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "active",
                                color = MaterialTheme.colors.primary,
                                style = MaterialTheme.typography.caption,
                            )
                        }
                    }
                    Text(
                        "🎯 1",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    )
                    Text("1", style = MaterialTheme.typography.body2)
                }
                CircularProgressRing(
                    progress = 0f,
                    centerTopText = "0%",
                    centerBottomText = "0 / 1 已完成",
                    size = 96.dp,
                )
            }
            Spacer(Modifier.height(14.dp))
            // 4 项指标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricColumn(
                    icon = Icons.Filled.Flag,
                    label = "总任务",
                    value = "1",
                    tint = MaterialTheme.colors.primary,
                )
                MetricColumn(
                    icon = Icons.Filled.HourglassEmpty,
                    label = "待提交",
                    value = "1",
                    tint = LearnSparkColors.Warning,
                )
                MetricColumn(
                    icon = Icons.Filled.CheckCircle,
                    label = "已通过",
                    value = "0",
                    tint = LearnSparkColors.Success,
                )
                MetricColumn(
                    icon = Icons.Filled.Schedule,
                    label = "每日时长",
                    value = "${project.dailyHours}h",
                    tint = LearnSparkColors.Info,
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyProjectCard(onCreate: () -> Unit) {
    StatCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "还没有项目",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "把你的学习目标拆成阶段和任务，让 AI 帮你规划学习路线。",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
            ActionButton(
                text = "新建项目",
                onClick = onCreate,
                background = MaterialTheme.colors.primary,
                foreground = MaterialTheme.colors.onPrimary,
                leadingIcon = Icons.Filled.Add,
            )
        }
    }
}

@Composable
private fun GroupHeader(name: String, count: Int, progress: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colors.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text("$count 个任务", style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
        }
        Spacer(Modifier.weight(1f))
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
    }
    Spacer(Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = progress,
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = MaterialTheme.colors.primary,
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.12f),
    )
}

@Composable
private fun TaskGroup(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    progress: Float,
    tasks: List<TaskUi>,
) {
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            tasks.forEachIndexed { i, t ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${t.index} ${t.title}", style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium)
                        Text("${t.index}", style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(LearnSparkColors.Warning.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(t.status, color = LearnSparkColors.Warning, style = MaterialTheme.typography.caption)
                    }
                }
            }
        }
    }
}

private data class TaskUi(val title: String, val index: Int, val status: String)

private data class ProjectsUiState(
    val project: ProjectDto? = null,
    val allProjects: List<ProjectDto> = emptyList(),
)

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, goal: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("学习目标（可选）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name, goal.ifBlank { "" }) },
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EditProjectDialog(
    initialName: String,
    initialGoal: String,
    onDismiss: () -> Unit,
    onSave: (name: String, goal: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var goal by remember { mutableStateOf(initialGoal) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("项目名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("学习目标") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, goal) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
