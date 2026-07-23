package com.learnspark.features.knowledge

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.learnspark.data.api.LearnSparkApi
import com.learnspark.data.db.KnowledgeFolderRepository
import com.learnspark.data.model.KnowledgeEntryDto
import com.learnspark.data.model.KnowledgeFolderDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * R3a + R5e：知识库 ViewModel（Voyager ScreenModel）。
 *
 * 职责：
 *  - 同步远端文件夹树
 *  - 同步远端条目（按 folderId 客户端过滤）
 *  - 提供新建/重命名/删除/移动
 *  - 提供 AI 整理（sugest + apply）
 *  - 10s 轮询条目列表，保证双端上传的文件能即时同步显示
 *
 * userId 来源：阶段 1.2 之前用 dev 固定值；后续由 TokenStore 解析。
 */
class KnowledgeViewModel(
    private val api: LearnSparkApi,
    private val repository: KnowledgeFolderRepository,
    private val userId: String = "00000000-0000-0000-0000-000000000001",
) : ScreenModel {

    val folders: StateFlow<List<KnowledgeFolderDto>> = repository.observeAll()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    private val _entries = MutableStateFlow<List<KnowledgeEntryDto>>(emptyList())
    val entries: StateFlow<List<KnowledgeEntryDto>> = _entries.asStateFlow()

    private val _ui = MutableStateFlow(KnowledgeUiState())
    val ui: StateFlow<KnowledgeUiState> = _ui.asStateFlow()

    init {
        refresh()
        startEntryPolling()
    }

    fun refresh() {
        screenModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val remote = api.listFolders(userId)
                remote.forEach { repository.upsert(KnowledgeFolderDto.fromMap(it)) }
                refreshEntries()
                _ui.value = _ui.value.copy(loading = false)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "unknown")
            }
        }
    }

    fun refreshEntries() {
        screenModelScope.launch {
            try {
                val remote = api.listKnowledgeEntries(userId, page = 0, size = 200)
                _entries.value = remote.map(KnowledgeEntryDto::fromMap)
            } catch (_: Exception) {
                // 静默：轮询失败不打扰用户
            }
        }
    }

    /**
     * R5e：10s 轮询条目列表。
     * - 仅在 ScreenModel 存活期间运行
     * - 失败静默（refresh 已暴露手动入口）
     */
    private fun startEntryPolling() {
        screenModelScope.launch {
            while (isActive) {
                delay(10_000)
                refreshEntries()
            }
        }
    }

    fun createFolder(name: String, parentId: String?, onCreated: ((KnowledgeFolderDto) -> Unit)? = null) {
        screenModelScope.launch {
            try {
                val raw = api.createFolder(userId, name, parentId)
                val dto = KnowledgeFolderDto.fromMap(raw)
                repository.upsert(dto)
                onCreated?.invoke(dto)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "新建失败：${e.message}")
            }
        }
    }

    fun renameFolder(id: String, newName: String) {
        screenModelScope.launch {
            try {
                val raw = api.updateFolder(userId, id, name = newName)
                repository.upsert(KnowledgeFolderDto.fromMap(raw))
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "重命名失败：${e.message}")
            }
        }
    }

    fun moveFolder(id: String, newParentId: String?) {
        screenModelScope.launch {
            try {
                val raw = api.moveFolder(userId, id, newParentId)
                repository.upsert(KnowledgeFolderDto.fromMap(raw))
                refresh() // 路径级联更新
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "移动失败：${e.message}")
            }
        }
    }

    fun deleteFolder(id: String) {
        screenModelScope.launch {
            try {
                api.deleteFolder(userId, id)
                refresh()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "删除失败：${e.message}")
            }
        }
    }

    fun deleteEntry(id: String) {
        screenModelScope.launch {
            try {
                api.deleteKnowledgeEntry(userId, id)
                refreshEntries()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "删除失败：${e.message}")
            }
        }
    }

    /**
     * R3c：AI 整理。entryIds 留空 → 服务端拉最近 30 条。
     * 返回 (entryId -> folderId|null) 的建议列表。
     */
    fun suggestOrganize(
        entryIds: List<String>? = null,
        onResult: (List<OrganizeSuggestion>) -> Unit = {},
    ) {
        screenModelScope.launch {
            try {
                val raw = api.suggestOrganize(userId, entryIds)
                val suggestions = raw.map { map ->
                    OrganizeSuggestion(
                        entryId = map["entryId"] as? String ?: "",
                        folderId = map["folderId"] as? String,
                        reason = map["reason"] as? String ?: "",
                    )
                }
                _ui.value = _ui.value.copy(lastSuggestions = suggestions)
                onResult(suggestions)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "AI 整理失败：${e.message}")
            }
        }
    }

    fun applyOrganize(acceptances: List<Triple<String, String?, String?>>) {
        screenModelScope.launch {
            try {
                api.applyOrganize(userId, acceptances)
                _ui.value = _ui.value.copy(lastSuggestions = emptyList())
                refreshEntries()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "应用失败：${e.message}")
            }
        }
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }

    data class KnowledgeUiState(
        val loading: Boolean = false,
        val error: String? = null,
        val lastSuggestions: List<OrganizeSuggestion> = emptyList(),
    )

    data class OrganizeSuggestion(
        val entryId: String,
        val folderId: String?,
        val reason: String,
    )
}
