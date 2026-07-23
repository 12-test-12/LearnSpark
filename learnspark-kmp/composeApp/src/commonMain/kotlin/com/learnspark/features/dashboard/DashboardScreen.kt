package com.learnspark.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.learnspark.core.security.SecureStorage
import com.learnspark.currentPlatform
import com.learnspark.data.api.LearnSparkApi
import com.learnspark.data.db.ProjectRepository
import com.learnspark.data.model.ProjectDto
import com.learnspark.shared.components.WeeklyHeatmap
import com.learnspark.shared.components.demoWeeklyHeatmap
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.UUID

object DashboardScreen : Screen {
    private fun readResolve(): Any = DashboardScreen

    @Composable
    override fun Content() {
        val repository: ProjectRepository = koinInject()
        val api: LearnSparkApi = koinInject()
        val secure: SecureStorage = koinInject()
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf("OK") }
        var projects by remember { mutableStateOf<List<ProjectDto>>(emptyList()) }
        val platform = "${currentPlatform()} | secure=${secure.platformName()}"

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("今日概览", style = MaterialTheme.typography.h5)

            // 阶段 3.3：7 日热力图
            WeeklyHeatmap(
                dailyProgress = demoWeeklyHeatmap(),
                modifier = Modifier.fillMaxWidth(),
            )

            // 待办占位
            Text("- 待完成任务：阶段 1.3 接入任务列表", style = MaterialTheme.typography.body2)
            Text("- 连续打卡：阶段 3.3 接入 streak", style = MaterialTheme.typography.body2)
            Text(
                "- AI 每日一句：阶段 3.3 接入云端",
                style = MaterialTheme.typography.caption,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ====== 阶段 0.2 / 0.3 / 1.1.3 / 1.1.6 PoC 入口 ======
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "本地同步 PoC（阶段 0.2 + 1.1.3 + 1.1.6）",
                        style = MaterialTheme.typography.subtitle1,
                    )
                    Text(platform, style = MaterialTheme.typography.caption)
                    Text("Status: $status", style = MaterialTheme.typography.caption)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        status = "local add..."
                                        val now = nowIso()
                                        val newProject = ProjectDto(
                                            id = UUID.randomUUID().toString(),
                                            name = "Project ${System.currentTimeMillis() / 1000}",
                                            goal = "PoC test",
                                            createdAt = now,
                                            updatedAt = now,
                                        )
                                        repository.upsert(newProject)
                                        projects = repository.getActiveProjects()
                                        status = "local add OK (dirty=true), count=${projects.size}"
                                    } catch (e: Exception) {
                                        status = "error: ${e.message}"
                                    }
                                }
                            },
                        ) { Text("Local Add") }

                        Button(
                            onClick = {
                                scope.launch {
                                    projects = repository.getActiveProjects()
                                    status = "loaded ${projects.size} local projects"
                                }
                            },
                        ) { Text("Load") }

                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        status = "pulling..."
                                        val resp = api.pullProjects(null)
                                        resp.projects.forEach {
                                            repository.upsert(it)
                                            repository.markClean(it.id)
                                        }
                                        projects = repository.getActiveProjects()
                                        status = "pulled ${resp.projects.size} projects"
                                    } catch (e: Exception) {
                                        status = "pull failed: ${e.message}"
                                    }
                                }
                            },
                        ) { Text("Pull") }

                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        status = "pushing..."
                                        val dirty = repository.getDirtyProjects()
                                        if (dirty.isEmpty()) {
                                            status = "no dirty data"
                                        } else {
                                            val resp = api.pushProjects(dirty)
                                            resp.accepted.forEach {
                                                repository.markClean(it.id)
                                            }
                                            status = "push ${resp.accepted.size}/${dirty.size} OK"
                                            projects = repository.getActiveProjects()
                                        }
                                    } catch (e: Exception) {
                                        status = "push failed: ${e.message}"
                                    }
                                }
                            },
                        ) { Text("Push") }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                        LazyColumn {
                            items(projects) { p ->
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                ) {
                                    Text("- ${p.name} (v${p.serverVersion + 1})")
                                    Text(
                                        "  ${p.goal ?: "no goal"}",
                                        style = MaterialTheme.typography.caption,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun nowIso(): String =
    java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
