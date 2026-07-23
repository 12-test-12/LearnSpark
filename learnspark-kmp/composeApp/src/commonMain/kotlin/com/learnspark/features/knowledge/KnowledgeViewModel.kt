package com.learnspark.features.knowledge

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.learnspark.core.files.FilePicker
import com.learnspark.core.files.PickedFile
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
                // 离线时静默：本地数据库已有缓存，UI 正常显示
                _ui.value = _ui.value.copy(loading = false)
            }
        }
    }

    fun refreshEntries() {
        screenModelScope.launch {
            try {
                val remote = api.listKnowledgeEntries(userId, page = 0, size = 200)
                _entries.value = remote.map(KnowledgeEntryDto::fromMap)
            } catch (_: Exception) {
                // 离线时静默：保留已有数据
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
                // 离线回退：服务端不可达时，本地创建文件夹
                // 文件夹保存到软件安装目录的 data/uploads 下
                val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
                val parent = parentId?.let { repository.getById(it) }
                val dto = KnowledgeFolderDto(
                    id = java.util.UUID.randomUUID().toString(),
                    userId = userId,
                    parentId = parentId,
                    name = name,
                    icon = "📁",
                    sortOrder = 0,
                    path = if (parent != null) "${parent.path}/${parent.name}" else "/",
                    depth = (parent?.depth ?: -1) + 1,
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )
                repository.upsert(dto)
                onCreated?.invoke(dto)
                _ui.value = _ui.value.copy(error = null)
            }
        }
    }

    fun renameFolder(id: String, newName: String) {
        screenModelScope.launch {
            try {
                val raw = api.updateFolder(userId, id, name = newName)
                repository.upsert(KnowledgeFolderDto.fromMap(raw))
            } catch (e: Exception) {
                // 离线回退：本地重命名
                val existing = repository.getById(id) ?: return@launch
                repository.upsert(existing.copy(name = newName, updatedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()))
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
                // 离线回退：本地软删除
                repository.softDelete(id)
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

    /**
     * R8：批量上传文件到知识库。
     * 弹出多文件选择器，逐个上传到服务端，并实时更新进度状态。
     */
    fun uploadFiles() {
        screenModelScope.launch {
            _ui.value = _ui.value.copy(uploading = true, uploadProgress = "正在选择文件…", error = null)
            val files = try {
                FilePicker.pickFiles(
                    title = "选择知识库文件（可多选）",
                    allowedExtensions = setOf("pdf", "md", "txt", "docx", "pptx", "xlsx", "html", "htm", "enex", "png", "jpg", "jpeg", "bmp"),
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(uploading = false, uploadProgress = null, error = "选择文件失败：${e.message}")
                return@launch
            }
            if (files.isEmpty()) {
                _ui.value = _ui.value.copy(uploading = false, uploadProgress = null)
                return@launch
            }
            var success = 0
            var failed = 0
            files.forEachIndexed { idx, file ->
                _ui.value = _ui.value.copy(uploadProgress = "上传中 ${idx + 1}/${files.size}：${file.name}")
                try {
                    api.uploadKnowledgeFile(userId, file.bytes, file.name)
                    success++
                } catch (e: Exception) {
                    failed++
                }
            }
            refreshEntries()
            _ui.value = _ui.value.copy(
                uploading = false,
                uploadProgress = null,
                error = if (failed > 0) "上传完成：成功 $success 个，失败 $failed 个" else null,
            )
        }
    }

    data class KnowledgeUiState(
        val loading: Boolean = false,
        val error: String? = null,
        val lastSuggestions: List<OrganizeSuggestion> = emptyList(),
        val uploading: Boolean = false,
        val uploadProgress: String? = null,
    )

    data class OrganizeSuggestion(
        val entryId: String,
        val folderId: String?,
        val reason: String,
    )
}
