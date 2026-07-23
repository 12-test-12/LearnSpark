package com.learnspark.features.tasks

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.learnspark.core.files.FilePicker
import com.learnspark.core.files.PickedFile
import com.learnspark.data.api.LearnSparkApi
import com.learnspark.data.db.KnowledgeFolderRepository
import com.learnspark.data.model.KnowledgeFolderDto
import com.learnspark.data.model.TaskArticleLinkDto
import com.learnspark.data.model.TaskUploadDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * R4c：任务详情 ViewModel。
 *
 * 功能：
 *   - 列出当前 task 的已上传文件
 *   - 调起文件选择器 → 上传 → 自动触发 AI 重新建议
 *   - 触发 AI 标注可参考文章
 *   - 列出/删除已绑定的知识库文章
 *   - 列出知识库文件夹供选择
 */
class TaskDetailViewModel(
    private val api: LearnSparkApi,
    private val folderRepository: KnowledgeFolderRepository,
) : ScreenModel {

    private val _uploads = MutableStateFlow<List<TaskUploadDto>>(emptyList())
    val uploads: StateFlow<List<TaskUploadDto>> = _uploads.asStateFlow()

    private val _articleLinks = MutableStateFlow<List<TaskArticleLinkDto>>(emptyList())
    val articleLinks: StateFlow<List<TaskArticleLinkDto>> = _articleLinks.asStateFlow()

    private val _folders = MutableStateFlow<List<KnowledgeFolderDto>>(emptyList())
    val folders: StateFlow<List<KnowledgeFolderDto>> = _folders.asStateFlow()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun init(taskId: String) {
        if (_ui.value.taskId == taskId && _uploads.value.isNotEmpty()) return
        _ui.update { it.copy(taskId = taskId) }
        refresh(taskId)
        loadFolders()
    }

    fun refresh(taskId: String = _ui.value.taskId) {
        if (taskId.isBlank()) return
        screenModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val userId = defaultUserId()
                _uploads.value = api.listTaskUploads(userId, taskId).map(::toUploadDto)
                _articleLinks.value = api.listArticleLinks(userId, taskId).map(::toLinkDto)
            } catch (e: Exception) {
                _ui.update { it.copy(error = "加载失败：${e.message}") }
            } finally {
                _ui.update { it.copy(loading = false) }
            }
        }
    }

    private fun loadFolders() {
        screenModelScope.launch {
            try {
                folderRepository.observeAll().collect { _folders.value = it }
            } catch (_: Exception) { }
        }
    }

    fun pickAndUpload(allowedExt: Set<String>, folderId: String? = null) {
        val taskId = _ui.value.taskId
        screenModelScope.launch {
            _ui.update { it.copy(uploading = true, error = null) }
            val picked: PickedFile? = try {
                FilePicker.pickFile(title = "选择文件", allowedExtensions = allowedExt)
            } catch (e: Exception) {
                _ui.update { it.copy(error = "选择文件失败：${e.message}") }
                null
            }
            if (picked == null) {
                _ui.update { it.copy(uploading = false) }
                return@launch
            }
            try {
                val userId = defaultUserId()
                api.uploadToTask(userId, taskId, picked.bytes, picked.name, folderId)
                _ui.update { it.copy(lastUploadName = picked.name) }
                refresh(taskId)
            } catch (e: Exception) {
                _ui.update { it.copy(error = "上传失败：${e.message}") }
            } finally {
                _ui.update { it.copy(uploading = false) }
            }
        }
    }

    fun deleteUpload(uploadId: String) {
        val taskId = _ui.value.taskId
        screenModelScope.launch {
            try {
                val userId = defaultUserId()
                api.deleteTaskUpload(userId, taskId, uploadId)
                refresh(taskId)
            } catch (e: Exception) {
                _ui.update { it.copy(error = "删除失败：${e.message}") }
            }
        }
    }

    fun suggestArticles(provider: String? = null) {
        val taskId = _ui.value.taskId
        screenModelScope.launch {
            _ui.update { it.copy(suggesting = true, error = null) }
            try {
                val userId = defaultUserId()
                val r = api.suggestArticleLinks(userId, taskId, provider)
                val applied = (r["applied"] as? Number)?.toInt() ?: 0
                _ui.update { it.copy(suggestResult = "AI 推荐了 $applied 篇可参考文章") }
                refresh(taskId)
            } catch (e: Exception) {
                _ui.update { it.copy(error = "AI 建议失败：${e.message}") }
            } finally {
                _ui.update { it.copy(suggesting = false) }
            }
        }
    }

    fun removeArticleLink(entryId: String) {
        val taskId = _ui.value.taskId
        screenModelScope.launch {
            try {
                val userId = defaultUserId()
                api.deleteArticleLink(userId, taskId, entryId)
                refresh(taskId)
            } catch (e: Exception) {
                _ui.update { it.copy(error = "删除失败：${e.message}") }
            }
        }
    }

    fun dismissError() {
        _ui.update { it.copy(error = null) }
    }

    fun dismissSuggest() {
        _ui.update { it.copy(suggestResult = null) }
    }

    data class UiState(
        val taskId: String = "",
        val loading: Boolean = false,
        val uploading: Boolean = false,
        val suggesting: Boolean = false,
        val error: String? = null,
        val lastUploadName: String? = null,
        val suggestResult: String? = null,
    )

    private fun toUploadDto(m: Map<String, Any?>): TaskUploadDto = TaskUploadDto(
        id = m["id"] as? String ?: "",
        taskId = m["taskId"] as? String ?: "",
        knowledgeEntryId = m["knowledgeEntryId"] as? String,
        folderId = m["folderId"] as? String,
        fileName = m["fileName"] as? String ?: "",
        fileType = m["fileType"] as? String ?: "",
        fileSize = (m["fileSize"] as? Number)?.toLong() ?: 0,
        uploadStatus = m["uploadStatus"] as? String ?: "pending",
        parseError = m["parseError"] as? String,
    )

    private fun toLinkDto(m: Map<String, Any?>): TaskArticleLinkDto = TaskArticleLinkDto(
        id = m["id"] as? String ?: "",
        taskId = m["taskId"] as? String ?: "",
        entryId = m["entryId"] as? String ?: "",
        reason = m["reason"] as? String ?: "",
        relevance = (m["relevance"] as? Number)?.toInt() ?: 50,
        source = m["source"] as? String ?: "ai",
    )
}

private fun defaultUserId(): String = "00000000-0000-0000-0000-000000000001"

/** 与服务端保持一致的"可解析扩展名"白名单。 */
val allowedUploadExt: Set<String> = setOf(
    "pdf", "docx", "pptx", "xlsx",
    "md", "markdown", "txt", "log", "rst", "adoc", "org", "tex",
    "html", "htm", "enex", "zip",
    "png", "jpg", "jpeg", "bmp", "tiff",
)
