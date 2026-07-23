package com.learnspark.server.api

import com.learnspark.server.domain.repository.KnowledgeEntryRepository
import com.learnspark.server.domain.repository.TaskUploadRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * R5a：跨端文件互通下载端点。
 *
 * 设计目标：PC 端上传到 task 的文件，移动端可拉取并查看。
 *
 * 端点：
 *  - GET /api/v1/knowledge/{id}/file        返回原始文件字节（含 Content-Type + 长度）
 *  - GET /api/v1/knowledge/{id}/text        返回已解析的纯文本（若解析失败则返回 422）
 *  - GET /api/v1/knowledge/{id}/meta        返回文件元数据（供客户端决定走哪条路径）
 *  - GET /api/v1/tasks/{taskId}/uploads/{id}/file
 *                                           任务上传的文件（同上）
 *
 * 鉴权：所有端点都要求 X-User-Id 必须等于资源 userId，否则 403。
 *
 * Content-Type 策略：优先按 fileType 扩展名推断，未知时回落 application/octet-stream。
 */
@RestController
class FileDownloadController(
    private val knowledgeRepository: KnowledgeEntryRepository,
    private val taskUploadRepository: TaskUploadRepository,
) {

    @Value("\${learnspark.storage.upload-dir:./data/uploads}")
    private lateinit var uploadDir: String

    // === 知识库条目 ===

    @GetMapping("/api/v1/knowledge/{id}/file")
    fun downloadKnowledgeFile(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val entry = knowledgeRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        if (entry.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body<Any>(mapOf("error" to "forbidden"))
        }
        val path = entry.sourcePath
            ?: return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body<Any>(mapOf("error" to "no_source_file", "reason" to "entry is not a file (sourceType=${entry.sourceType.name})"))
        return serveFile(path, entry.fileType, entry.fileName() ?: extractNameFromPath(path))
    }

    @GetMapping("/api/v1/knowledge/{id}/text")
    fun getKnowledgeText(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val entry = knowledgeRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        if (entry.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body<Any>(mapOf("error" to "forbidden"))
        }
        val text = entry.content
        if (text.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body<Any>(
                mapOf(
                    "error" to "no_text",
                    "parseStatus" to entry.parseStatus.name,
                    "parseError" to entry.parseError,
                )
            )
        }
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body<Any>(text)
    }

    @GetMapping("/api/v1/knowledge/{id}/meta")
    fun getKnowledgeMeta(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val entry = knowledgeRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        if (entry.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body<Any>(mapOf("error" to "forbidden"))
        }
        return ResponseEntity.ok<Any>(
            mapOf(
                "id" to entry.id,
                "title" to entry.title,
                "fileType" to entry.fileType,
                "fileSize" to entry.fileSize,
                "parseStatus" to entry.parseStatus.name,
                "parseError" to entry.parseError,
                "contentLength" to (entry.content?.length ?: 0),
                "hasSourceFile" to (!entry.sourcePath.isNullOrBlank() && File(entry.sourcePath!!).exists()),
                "sourceType" to entry.sourceType.name,
                "version" to entry.version,
                "updatedAt" to entry.updatedAt?.toString(),
            )
        )
    }

    // === 任务上传文件 ===

    @GetMapping("/api/v1/tasks/{taskId}/uploads/{id}/file")
    fun downloadTaskUploadFile(
        @PathVariable taskId: String,
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val upload = taskUploadRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        if (upload.userId != userId || upload.taskId != taskId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body<Any>(mapOf("error" to "forbidden"))
        }
        if (upload.filePath.isBlank() || !File(upload.filePath).exists()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body<Any>(mapOf("error" to "file_missing_on_server"))
        }
        return serveFile(upload.filePath, upload.fileType, upload.fileName)
    }

    // === helpers ===

    private fun serveFile(absolutePath: String, fileType: String?, displayName: String): ResponseEntity<Any> {
        val file = File(absolutePath)
        if (!file.exists() || !file.isFile) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body<Any>(mapOf("error" to "file_missing", "path" to absolutePath))
        }
        val resource: Resource = FileSystemResource(file)
        val mediaType = guessMediaType(fileType, file.name)
        val encodedName = java.net.URLEncoder.encode(displayName, Charsets.UTF_8)
            .replace("+", "%20")
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(file.length())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"$displayName\"; filename*=UTF-8''$encodedName"
            )
            .header("X-File-Size", file.length().toString())
            .body<Any>(resource)
    }

    private fun guessMediaType(fileType: String?, fileName: String): MediaType {
        val ext = (fileType?.removePrefix(".") ?: fileName.substringAfterLast('.', ""))
            .lowercase()
        val mime: MediaType = when (ext) {
            "pdf" -> MediaType.APPLICATION_PDF
            "md", "markdown", "txt", "log", "rst", "adoc", "org", "tex" -> MediaType.TEXT_PLAIN
            "html", "htm" -> MediaType.TEXT_HTML
            "xml" -> MediaType.APPLICATION_XML
            "json" -> MediaType.APPLICATION_JSON
            "csv" -> MediaType.parseMediaType("text/csv")
            "png" -> MediaType.IMAGE_PNG
            "jpg", "jpeg" -> MediaType.IMAGE_JPEG
            "gif" -> MediaType.IMAGE_GIF
            "bmp" -> MediaType.parseMediaType("image/bmp")
            "webp" -> MediaType.parseMediaType("image/webp")
            "svg" -> MediaType.parseMediaType("image/svg+xml")
            "tiff", "tif" -> MediaType.parseMediaType("image/tiff")
            "mp3" -> MediaType.parseMediaType("audio/mpeg")
            "mp4" -> MediaType.parseMediaType("video/mp4")
            "zip" -> MediaType.parseMediaType("application/zip")
            "docx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            "pptx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
            "xlsx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            "doc" -> MediaType.parseMediaType("application/msword")
            "ppt" -> MediaType.parseMediaType("application/vnd.ms-powerpoint")
            "xls" -> MediaType.parseMediaType("application/vnd.ms-excel")
            "enex" -> MediaType.parseMediaType("application/xml")
            else -> MediaType.APPLICATION_OCTET_STREAM
        }
        return mime
    }

    private fun extractNameFromPath(path: String): String = Paths.get(path).fileName.toString()
}

/** R5 helper: 实体补充 fileName 推断 (title 优先；否则 fileType 推断）。 */
private fun com.learnspark.server.domain.entity.KnowledgeEntry.fileName(): String? {
    // KnowledgeEntry 没有 fileName 字段，使用 title
    return if (title.isNotBlank() && title != "[deleted] $title") title else null
}
