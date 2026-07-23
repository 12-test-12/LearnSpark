package com.learnspark.server.api

import com.learnspark.server.domain.entity.Submission
import com.learnspark.server.service.SubmissionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * R2：Submission REST API。
 *
 * - GET    /api/v1/submissions?taskId=...   列出指定 task 的所有提交
 * - GET    /api/v1/submissions/{id}         详情
 * - POST   /api/v1/submissions              新建（body 含 taskId + content），触发 AI 评审
 * - PATCH  /api/v1/submissions/{id}         更新 content（不自动重评）
 * - DELETE /api/v1/submissions/{id}         删除
 */
@RestController
@RequestMapping("/api/v1/submissions")
class SubmissionController(
    private val service: SubmissionService,
) {

    @GetMapping
    fun listByTask(
        @RequestParam taskId: String,
        @RequestHeader("X-User-Id") userId: String,
    ): Map<String, Any?> = mapOf(
        "items" to service.listByTask(taskId, userId).map(::toDto)
    )

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val s = service.get(id, userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(s))
    }

    @PostMapping
    fun create(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: CreateRequest,
    ): ResponseEntity<Any> = when (
        val result = service.create(
            taskId = req.taskId,
            userId = userId,
            content = req.content,
            triggerReview = req.triggerReview ?: true,
        )
    ) {
        is SubmissionService.CreateResult.Ok ->
            ResponseEntity.status(HttpStatus.ACCEPTED).body(toDto(result.submission))
        SubmissionService.CreateResult.TaskNotFound ->
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "task_not_found"))
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: UpdateRequest,
    ): ResponseEntity<Any> = when (
        val result = service.update(
            id = id,
            userId = userId,
            content = req.content,
            baseVersion = req.baseVersion,
        )
    ) {
        is SubmissionService.UpdateResult.Ok -> ResponseEntity.ok(toDto(result.submission))
        SubmissionService.UpdateResult.NotFound -> ResponseEntity.notFound().build()
        is SubmissionService.UpdateResult.VersionConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(
            mapOf(
                "error" to "version_conflict",
                "currentVersion" to result.currentVersion,
                "current" to toDto(result.current),
            )
        )
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> = if (service.delete(id, userId)) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.notFound().build()
    }

    private fun toDto(s: Submission) = mapOf(
        "id" to s.id,
        "taskId" to s.taskId,
        "userId" to s.userId,
        "content" to s.content,
        "aiScore" to s.aiScore,
        "aiFeedback" to s.aiFeedback,
        "aiHighlights" to s.aiHighlights,
        "reviewedAt" to s.reviewedAt?.toString(),
        "version" to s.version,
        "createdAt" to s.createdAt?.toString(),
        "updatedAt" to s.updatedAt?.toString(),
    )

    data class CreateRequest(
        val taskId: String,
        val content: String,
        val triggerReview: Boolean? = null,
    )

    data class UpdateRequest(
        val content: String? = null,
        val baseVersion: Long? = null,
    )
}
