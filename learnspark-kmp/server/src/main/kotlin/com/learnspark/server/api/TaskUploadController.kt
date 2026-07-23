package com.learnspark.server.api

import com.learnspark.server.service.FileParseService
import com.learnspark.server.service.TaskUploadService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * R4c：任务上传 + 资料管理 API。
 *
 * - POST   /api/v1/tasks/{taskId}/uploads          上传文件（绑定到 task + 可选 folderId）
 * - GET    /api/v1/tasks/{taskId}/uploads          列出该 task 的全部上传
 * - DELETE /api/v1/tasks/{taskId}/uploads/{id}     删除一个上传
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/uploads")
class TaskUploadController(
    private val service: TaskUploadService,
) {

    @PostMapping(consumes = ["multipart/form-data"])
    fun upload(
        @PathVariable taskId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false) folderId: String?,
    ): ResponseEntity<Any> = when (val r = service.upload(userId, taskId, file, folderId)) {
        is TaskUploadService.UploadResult.Ok -> {
            val upload = r.upload
            ResponseEntity.status(HttpStatus.CREATED).body(
                mapOf(
                    "upload" to toDto(upload),
                    "knowledgeEntryId" to r.knowledgeEntryId,
                    "parse" to when (val p = r.parse) {
                        is FileParseService.SubmitResult.SyncReady -> mapOf(
                            "mode" to "sync",
                            "status" to "READY",
                            "textLength" to p.text.length,
                            "pages" to p.pages,
                        )
                        is FileParseService.SubmitResult.AsyncPending -> mapOf(
                            "mode" to "async",
                            "status" to "PENDING",
                            "jobId" to p.jobId,
                        )
                    },
                )
            )
        }
        TaskUploadService.UploadResult.TaskNotFound -> ResponseEntity.notFound().build()
        TaskUploadService.UploadResult.Empty -> ResponseEntity.badRequest().body(mapOf("error" to "file_empty"))
        is TaskUploadService.UploadResult.TooLarge -> ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(mapOf("error" to "file_too_large", "actual" to r.actual, "max" to r.max))
        is TaskUploadService.UploadResult.UnsupportedFormat -> ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(mapOf("error" to "unsupported_format", "ext" to r.ext))
        TaskUploadService.UploadResult.FolderNotFound -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "folder_not_found"))
        is TaskUploadService.UploadResult.ParseFailed -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(mapOf("error" to "parse_failed", "detail" to r.error))
    }

    @GetMapping
    fun list(
        @PathVariable taskId: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val items = service.list(userId, taskId)
        return ResponseEntity.ok(mapOf("items" to items.map(::toDto)))
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable taskId: String,
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> = if (service.delete(userId, id)) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.notFound().build()
    }

    private fun toDto(u: com.learnspark.server.domain.entity.TaskUpload) = mapOf(
        "id" to u.id,
        "taskId" to u.taskId,
        "userId" to u.userId,
        "knowledgeEntryId" to u.knowledgeEntryId,
        "folderId" to u.folderId,
        "fileName" to u.fileName,
        "fileType" to u.fileType,
        "fileSize" to u.fileSize,
        "uploadStatus" to u.uploadStatus,
        "parseError" to u.parseError,
        "version" to u.version,
        "createdAt" to u.createdAt?.toString(),
        "updatedAt" to u.updatedAt?.toString(),
    )
}
