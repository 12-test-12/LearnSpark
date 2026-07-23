package com.learnspark.features.viewer

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.learnspark.core.files.FileCache
import com.learnspark.core.files.ViewKind
import com.learnspark.core.files.classifyForView
import com.learnspark.core.files.guessMimeType
import com.learnspark.core.files.openWithSystem
import com.learnspark.data.api.LearnSparkApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * R5d：文件查看器 ViewModel。
 *
 * 数据源：
 *  - Knowledge Entry (fileType=FILE 即可下载)
 *  - Task Upload (fileType 必填)
 *  - 已缓存的本地文件（key）
 *  - 纯文本内容（直接传入 parsedText，跳过下载）
 *
 * 流程：
 *  1. 解析 ViewKind（IMAGE / TEXT / OPEN_EXTERNAL）
 *  2. 文本类型优先用服务端解析后的纯文本
 *  3. 否则下载原始字节到本地 cache，再按 viewKind 渲染
 */
class FileViewerViewModel(
    private val api: LearnSparkApi,
    private val fileCache: FileCache,
) : ScreenModel {

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastCacheKey: String? = null

    fun load(userId: String, source: Source) {
        screenModelScope.launch {
            _state.value = UiState.Loading
            try {
                when (source) {
                    is Source.Knowledge -> loadKnowledge(userId, source)
                    is Source.TaskUpload -> loadTaskUpload(userId, source)
                    is Source.Text -> _state.value = UiState.Text(source.text)
                    is Source.CacheKey -> {
                        val bytes = fileCache.get(source.key)
                        if (bytes == null) {
                            _state.value = UiState.Error("缓存文件不存在：${source.key}")
                        } else {
                            lastCacheKey = source.key
                            _state.value = UiState.Bytes(
                                bytes = bytes,
                                fileName = source.fileName,
                                fileType = source.fileType,
                                viewKind = classifyForView(source.fileType, source.fileName),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = UiState.Error("加载失败：${e.message ?: "未知错误"}")
            }
        }
    }

    private suspend fun loadKnowledge(userId: String, source: Source.Knowledge) {
        val kind = classifyForView(source.fileType, source.fileName)
        if (kind == ViewKind.TEXT) {
            val text = api.getKnowledgeText(userId, source.entryId)
            if (!text.isNullOrBlank()) {
                _state.value = UiState.Text(text)
                return
            }
        }
        val key = "knowledge:${source.entryId}"
        val bytes = fileCache.get(key) ?: run {
            val downloaded = api.downloadKnowledgeFile(userId, source.entryId)
            fileCache.put(key, downloaded)
            downloaded
        }
        lastCacheKey = key
        _state.value = UiState.Bytes(
            bytes = bytes,
            fileName = source.fileName,
            fileType = source.fileType,
            viewKind = kind,
        )
    }

    private suspend fun loadTaskUpload(userId: String, source: Source.TaskUpload) {
        val key = "task_upload:${source.uploadId}"
        val bytes = fileCache.get(key) ?: run {
            val downloaded = api.downloadTaskUploadFile(userId, source.taskId, source.uploadId)
            fileCache.put(key, downloaded)
            downloaded
        }
        lastCacheKey = key
        _state.value = UiState.Bytes(
            bytes = bytes,
            fileName = source.fileName,
            fileType = source.fileType,
            viewKind = classifyForView(source.fileType, source.fileName),
        )
    }

    fun openWithSystem() {
        screenModelScope.launch {
            val key = lastCacheKey ?: return@launch
            val cur = _state.value as? UiState.Bytes ?: return@launch
            val path = fileCache.pathFor(key) ?: return@launch
            openWithSystem(path, guessMimeType(cur.fileType, cur.fileName))
        }
    }

    fun exportToDocuments() {
        screenModelScope.launch {
            val key = lastCacheKey ?: return@launch
            val cur = _state.value as? UiState.Bytes ?: return@launch
            fileCache.exportToDocuments(key, cur.fileName)
        }
    }

    sealed class Source {
        data class Knowledge(
            val entryId: String,
            val fileName: String,
            val fileType: String?,
        ) : Source()

        data class TaskUpload(
            val taskId: String,
            val uploadId: String,
            val fileName: String,
            val fileType: String,
        ) : Source()

        data class Text(val text: String) : Source()
        data class CacheKey(val key: String, val fileName: String, val fileType: String?) : Source()
    }

    sealed class UiState {
        data object Loading : UiState()
        data class Text(val text: String) : UiState()
        data class Bytes(
            val bytes: ByteArray,
            val fileName: String,
            val fileType: String?,
            val viewKind: ViewKind,
        ) : UiState()
        data class Error(val message: String) : UiState()
    }
}
