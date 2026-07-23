package com.learnspark.server.api

import com.learnspark.server.domain.entity.Task
import com.learnspark.server.service.TaskService
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
import java.time.LocalDate

/**
 * R1：Task REST API。
 *
 * - GET    /api/v1/tasks?phaseId=...        列出指定 phase 下 tasks
 * - GET    /api/v1/tasks/{id}               详情
 * - POST   /api/v1/tasks                    新建（body 含 phaseId）
 * - PATCH  /api/v1/tasks/{id}               部分更新（status 变化触发 phase 状态聚合）
 * - DELETE /api/v1/tasks/{id}               真删（触发 phase 状态聚合）
 *
 * 跨级权限：所有读写都校验 task.phaseId.projectId.userId == header
 */
@RestController
@RequestMapping("/api/v1/tasks")
class TaskController(
    private val taskService: TaskService,
) {

    @GetMapping
    fun listByPhase(
        @RequestParam phaseId: String,
        @RequestHeader("X-User-Id") userId: String,
    ): Map<String, Any?> = mapOf(
        "items" to taskService.listByPhase(phaseId, userId).map(::toDto)
    )

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val task = taskService.get(id, userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(task))
    }

    @PostMapping
    fun create(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: CreateRequest,
    ): ResponseEntity<Any> = try {
        when (val result = taskService.create(
            phaseId = req.phaseId,
            userId = userId,
            title = req.title,
            description = req.description,
            sortOrder = req.sortOrder ?: 0,
            estimatedHours = req.estimatedHours ?: 1,
            dueDate = req.dueDate,
        )) {
            is TaskService.CreateResult.Ok ->
                ResponseEntity.status(HttpStatus.CREATED).body(toDto(result.task))
            TaskService.CreateResult.PhaseNotFound ->
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "phase_not_found"))
        }
    } catch (e: IllegalArgumentException) {
        ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "invalid_request")))
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: UpdateRequest,
    ): ResponseEntity<Any> = try {
        when (val result = taskService.update(
            id = id,
            userId = userId,
            title = req.title,
            description = req.description,
            sortOrder = req.sortOrder,
            estimatedHours = req.estimatedHours,
            actualHours = req.actualHours,
            status = req.status,
            dueDate = req.dueDate,
            baseVersion = req.baseVersion,
        )) {
            is TaskService.UpdateResult.Ok -> ResponseEntity.ok(toDto(result.task))
            TaskService.UpdateResult.NotFound -> ResponseEntity.notFound().build()
            is TaskService.UpdateResult.VersionConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "error" to "version_conflict",
                    "currentVersion" to result.currentVersion,
                    "current" to toDto(result.current),
                )
            )
        }
    } catch (e: IllegalArgumentException) {
        ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "invalid_request")))
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> = if (taskService.delete(id, userId)) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.notFound().build()
    }

    private fun toDto(t: Task) = mapOf(
        "id" to t.id,
        "phaseId" to t.phaseId,
        "title" to t.title,
        "description" to t.description,
        "sortOrder" to t.sortOrder,
        "estimatedHours" to t.estimatedHours,
        "actualHours" to t.actualHours,
        "status" to t.status,
        "dueDate" to t.dueDate?.toString(),
        "version" to t.version,
        "createdAt" to t.createdAt?.toString(),
        "updatedAt" to t.updatedAt?.toString(),
    )

    data class CreateRequest(
        val phaseId: String,
        val title: String,
        val description: String? = null,
        val sortOrder: Int? = null,
        val estimatedHours: Int? = null,
        val dueDate: LocalDate? = null,
    )

    data class UpdateRequest(
        val title: String? = null,
        val description: String? = null,
        val sortOrder: Int? = null,
        val estimatedHours: Int? = null,
        val actualHours: Int? = null,
        val status: String? = null,
        val dueDate: LocalDate? = null,
        val baseVersion: Long? = null,
    )
}
