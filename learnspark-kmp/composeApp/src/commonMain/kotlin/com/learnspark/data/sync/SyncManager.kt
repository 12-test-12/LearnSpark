package com.learnspark.data.sync

import com.learnspark.data.api.LearnSparkApi
import com.learnspark.data.db.KnowledgeFolderRepository
import com.learnspark.data.db.ProjectRepository
import com.learnspark.data.db.ReminderRepository
import com.learnspark.data.model.KnowledgeFolderDto
import com.learnspark.data.model.ReminderSettingDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 跨端数据同步管理器。
 *
 * 核心策略：本地优先（Local-First）
 * 1. 所有写操作先写入本地 SQLDelight 数据库，标记 isDirty=true
 * 2. 后台每 30s 检查 dirty 数据，推送到服务端
 * 3. 同时拉取服务端最新数据，合并到本地
 * 4. AI 功能仍需联网，其余功能离线可用
 *
 * PC 端和移动端连同一个服务端 → 数据自动同步。
 */
class SyncManager(
    private val api: LearnSparkApi,
    private val projectRepository: ProjectRepository,
    private val folderRepository: KnowledgeFolderRepository,
    private val reminderRepository: ReminderRepository,
    private val userId: String = "00000000-0000-0000-0000-000000000001",
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /**
     * 启动后台同步循环。
     * 每 30s 执行一次 push + pull。
     */
    fun start() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            // 首次立即同步一次
            doSync()
            while (isActive) {
                delay(30_000)
                doSync()
            }
        }
    }

    /**
     * 手动触发一次同步（用户点"刷新"时调用）。
     */
    fun syncNow() {
        scope.launch { doSync() }
    }

    /**
     * 停止后台同步。
     */
    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }

    private suspend fun doSync() {
        _state.value = _state.value.copy(syncing = true, lastError = null)
        var pushOk = false
        var pullOk = false
        var error: String? = null

        // === Push：把本地 dirty 数据推到服务端 ===
        try {
            pushDirtyProjects()
            pushDirtyFolders()
            pushDirtyReminders()
            pushOk = true
        } catch (e: Exception) {
            error = "推送失败：${e.message}"
        }

        // === Pull：拉取服务端最新数据到本地 ===
        try {
            pullProjects()
            pullFolders()
            pullReminders()
            pullOk = true
        } catch (e: Exception) {
            error = error ?: "拉取失败：${e.message}"
        }

        _state.value = _state.value.copy(
            syncing = false,
            lastSyncAt = System.currentTimeMillis(),
            lastError = if (pushOk || pullOk) null else error,
            online = pushOk || pullOk,
        )
    }

    // === Projects ===

    private suspend fun pushDirtyProjects() {
        val dirty = projectRepository.getDirtyProjects()
        if (dirty.isEmpty()) return
        api.pushProjects(dirty)
        dirty.forEach { projectRepository.markClean(it.id) }
    }

    private suspend fun pullProjects() {
        var cursor: String? = null
        do {
            val resp = api.pullProjects(cursor)
            resp.projects.forEach { p ->
                // 服务端数据覆盖本地（服务端为准）
                projectRepository.upsert(p.copy(isDirty = false))
            }
            cursor = resp.nextCursor
        } while (cursor != null)
    }

    // === Knowledge Folders ===

    private suspend fun pushDirtyFolders() {
        val dirty = folderRepository.getDirty()
        if (dirty.isEmpty()) return
        dirty.forEach { folder ->
            try {
                if (folder.version == 0L) {
                    // 本地新建的 → 推到服务端
                    api.createFolder(userId, folder.name, folder.parentId)
                    // 用 upsert 清除 dirty 标记（服务端返回的会覆盖本地）
                    folderRepository.upsert(folder.copy(version = 1L))
                } else {
                    // 已有的 → 更新
                    api.updateFolder(userId, folder.id, name = folder.name)
                    // 清除 dirty 标记
                    folderRepository.upsert(folder.copy())
                }
            } catch (_: Exception) {
                // 单条失败不影响其他
            }
        }
    }

    private suspend fun pullFolders() {
        val remote = api.listFolders(userId)
        remote.forEach { folderRepository.upsert(KnowledgeFolderDto.fromMap(it)) }
    }

    // === Reminders ===

    private suspend fun pushDirtyReminders() {
        // ReminderRepository 暂无 getDirty 方法，跳过
        // 后续可通过 isDirty 字段查询
    }

    private suspend fun pullReminders() {
        val remote = api.listReminderSettings(userId)
        remote.forEach { map ->
            reminderRepository.upsertSetting(ReminderSettingDto.fromMap(map))
        }
    }

    data class SyncState(
        val syncing: Boolean = false,
        val lastSyncAt: Long = 0L,
        val lastError: String? = null,
        val online: Boolean = true,
    )
}
