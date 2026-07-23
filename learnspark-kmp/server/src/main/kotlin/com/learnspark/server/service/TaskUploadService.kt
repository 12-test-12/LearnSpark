package com.learnspark.server.service

import com.learnspark.server.domain.entity.KnowledgeEntry
import com.learnspark.server.domain.entity.TaskUpload
import com.learnspark.server.domain.repository.KnowledgeEntryRepository
import com.learnspark.server.domain.repository.KnowledgeFolderRepository
import com.learnspark.server.domain.repository.TaskUploadRepository
import com.learnspark.server.service.parsers.ParserRouter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * R4c：任务上传服务。
 *
 * 用户在任务详情页上传文件 → 落盘 → 落 knowledge_entries → 关联到 task_uploads。
 *
 * 复用 FileParseService 的能力做同步/异步解析。
 */
@Service
class TaskUploadService(
    private val taskService: TaskService,
    private val taskUploadRepository: TaskUploadRepository,
    private val knowledgeService: KnowledgeService,
    private val knowledgeRepository: KnowledgeEntryRepository,
    private val knowledgeFolderRepository: KnowledgeFolderRepository,
    private val parserRouter: ParserRouter,
    private val fileParseService: FileParseService,

    @Value("\${learnspark.storage.upload-dir:./data/uploads}")
    private val uploadDir: String,

    @Value("\${learnspark.storage.max-file-size-bytes:52428800}")
    private val maxFileSize: Long,
) {
    private val log = LoggerFactory.getLogger(TaskUploadService::class.java)

    @Transactional
    fun upload(
        userId: String,
        taskId: String,
        file: MultipartFile,
        folderId: String? = null,
    ): UploadResult {
        if (taskService.get(taskId, userId) == null) return UploadResult.TaskNotFound
        if (file.isEmpty) return UploadResult.Empty
        if (file.size > maxFileSize) return UploadResult.TooLarge(file.size, maxFileSize)

        val originalName = file.originalFilename ?: "unknown"
        val ext = originalName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase().let { if (it.isNotEmpty()) ".$it" else "" }
        if (ext.isBlank() || !parserRouter.supports(ext)) {
            return UploadResult.UnsupportedFormat(ext)
        }
        // 校验 folderId 归属
        if (folderId != null && knowledgeFolderRepository.findByIdAndUserId(folderId, userId) == null) {
            return UploadResult.FolderNotFound
        }

        // 1. 保存文件
        val targetDir = Paths.get(uploadDir, userId, taskId).toFile().apply { mkdirs() }
        val savedFile = File(targetDir, "${UUID.randomUUID()}$ext")
        Files.copy(file.inputStream, savedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        log.info("[taskUpload] saved: task={}, file={}, size={}", taskId, savedFile.name, file.size)

        // 2. 创建知识条目（folderId 由用户选定）
        val entry: KnowledgeEntry = knowledgeService.create(
            userId = userId,
            title = originalName,
            content = null,
            sourceType = KnowledgeEntry.SourceType.FILE,
        )
        entry.sourcePath = savedFile.absolutePath
        entry.fileSize = file.size
        entry.fileType = ext
        entry.folderId = folderId
        entry.parseStatus = KnowledgeEntry.ParseStatus.PENDING
        val persisted: KnowledgeEntry = knowledgeRepository.save(entry)

        // 3. 创建 task_uploads 记录
        val upload = TaskUpload(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            userId = userId,
            knowledgeEntryId = persisted.id,
            folderId = folderId,
            fileName = originalName,
            filePath = savedFile.absolutePath,
            fileType = ext,
            fileSize = file.size,
            uploadStatus = "parsing",
            version = 1L,
        )
        val savedUpload = taskUploadRepository.save(upload)

        // 4. 提交解析
        val parseResult = try {
            fileParseService.submit(
                entryId = persisted.id,
                filePath = savedFile.absolutePath,
                fileType = ext,
                fileSize = file.size,
            )
        } catch (e: Exception) {
            log.warn("[taskUpload] parse submit failed: {}", e.message)
            savedUpload.uploadStatus = "failed"
            savedUpload.parseError = e.message?.take(1000)
            savedUpload.version = savedUpload.version + 1
            taskUploadRepository.save(savedUpload)
            return UploadResult.ParseFailed(savedUpload, e.message ?: "unknown")
        }
        when (parseResult) {
            is FileParseService.SubmitResult.SyncReady -> {
                savedUpload.uploadStatus = "ready"
                savedUpload.version = savedUpload.version + 1
                taskUploadRepository.save(savedUpload)
            }
            is FileParseService.SubmitResult.AsyncPending -> {
                // worker 处理完会更新 status
            }
        }
        return UploadResult.Ok(savedUpload, persisted.id, parseResult)
    }

    @Transactional(readOnly = true)
    fun list(userId: String, taskId: String): List<TaskUpload> {
        if (taskService.get(taskId, userId) == null) return emptyList()
        return taskUploadRepository.findByTaskIdAndUserIdOrderByCreatedAtDesc(taskId, userId)
    }

    @Transactional
    fun delete(userId: String, uploadId: String): Boolean {
        val u = taskUploadRepository.findByIdAndUserId(uploadId, userId) ?: return false
        // 删除文件
        runCatching { File(u.filePath).delete() }
        // 关联的 knowledge_entry 也删除（用户上传的"任务资料"，与任务同生命周期）
        u.knowledgeEntryId?.let { eid ->
            runCatching { knowledgeRepository.deleteById(eid) }
        }
        taskUploadRepository.delete(u)
        return true
    }

    sealed class UploadResult {
        data class Ok(
            val upload: TaskUpload,
            val knowledgeEntryId: String,
            val parse: FileParseService.SubmitResult,
        ) : UploadResult()
        data object TaskNotFound : UploadResult()
        data object Empty : UploadResult()
        data class TooLarge(val actual: Long, val max: Long) : UploadResult()
        data class UnsupportedFormat(val ext: String) : UploadResult()
        data object FolderNotFound : UploadResult()
        data class ParseFailed(val upload: TaskUpload, val error: String) : UploadResult()
    }
}
