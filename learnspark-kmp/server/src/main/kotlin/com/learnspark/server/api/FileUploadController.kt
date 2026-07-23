package com.learnspark.server.api

import com.learnspark.server.domain.entity.KnowledgeEntry
import com.learnspark.server.domain.repository.KnowledgeEntryRepository
import com.learnspark.server.service.FileParseService
import com.learnspark.server.service.KnowledgeService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * 阶段 2.2 + 2.3：文件上传 + 解析触发端点。
 *
 * POST /api/v1/knowledge/upload
 *   - file:  上传的文件（multipart/form-data）
 *   - title: （可选）知识标题，默认用文件名
 *   - 流程：
 *     1. 保存文件到 uploadDir
 *     2. 创建 KnowledgeEntry（parseStatus=PENDING）
 *     3. FileParseService.submit：
 *        - < 1MB：同步解析
 *        - >= 1MB：写 PENDING 任务，app-worker 轮询处理
 *     4. 返回 entry + parseStatus
 */
@RestController
@RequestMapping("/api/v1/knowledge")
class FileUploadController(
    private val knowledgeService: KnowledgeService,
    private val knowledgeRepository: KnowledgeEntryRepository,
    private val fileParseService: FileParseService,

    @Value("\${learnspark.storage.upload-dir:./data/uploads}")
    private val uploadDir: String,

    @Value("\${learnspark.storage.max-file-size-bytes:52428800}")
    private val maxFileSize: Long,
) {
    private val log = LoggerFactory.getLogger(FileUploadController::class.java)

    @PostMapping("/upload", consumes = ["multipart/form-data"])
    fun upload(
        @RequestHeader("X-User-Id") userId: String,
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false) title: String?,
    ): ResponseEntity<Any> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body(mapOf("error" to "file is empty"))
        }
        if (file.size > maxFileSize) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                mapOf("error" to "file_too_large", "maxBytes" to maxFileSize, "actual" to file.size)
            )
        }

        val originalName = file.originalFilename ?: "unknown"
        val ext = originalName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            .let { if (it.isNotEmpty()) ".$it" else "" }

        // 1. 保存到本地
        val targetDir = Paths.get(uploadDir, userId).toFile().apply { mkdirs() }
        val savedFile = File(targetDir, "${UUID.randomUUID()}$ext")
        Files.copy(file.inputStream, savedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        log.info("[upload] saved: user={}, file={}, size={}", userId, savedFile.name, file.size)

        // 2. 创建知识条目
        val entry: KnowledgeEntry = knowledgeService.create(
            userId = userId,
            title = title ?: originalName,
            content = null,
            sourceType = KnowledgeEntry.SourceType.FILE,
        )
        // 补全文件信息（直接 set，避免使用 .copy() 因为 entity 非 data class）
        entry.sourcePath = savedFile.absolutePath
        entry.fileSize = file.size
        entry.fileType = ext
        entry.parseStatus = KnowledgeEntry.ParseStatus.PENDING
        val persisted: KnowledgeEntry = knowledgeRepository.save(entry)

        // 3. 提交解析
        val result = fileParseService.submit(
            entryId = persisted.id,
            filePath = savedFile.absolutePath,
            fileType = ext,
            fileSize = file.size,
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "entry" to mapOf(
                    "id" to persisted.id,
                    "title" to persisted.title,
                    "fileType" to persisted.fileType,
                    "fileSize" to persisted.fileSize,
                ),
                "parse" to when (result) {
                    is FileParseService.SubmitResult.SyncReady -> mapOf(
                        "mode" to "sync",
                        "status" to "READY",
                        "textLength" to result.text.length,
                        "pages" to result.pages,
                    )
                    is FileParseService.SubmitResult.AsyncPending -> mapOf(
                        "mode" to "async",
                        "status" to "PENDING",
                        "jobId" to result.jobId,
                    )
                },
            )
        )
    }
}
