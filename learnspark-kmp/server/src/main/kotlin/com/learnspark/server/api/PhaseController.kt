package com.learnspark.server.api

import com.learnspark.server.domain.entity.Phase
import com.learnspark.server.service.PhaseService
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
 * R1：Phase REST API。
 *
 * - GET    /api/v1/phases?projectId=...    列出指定 project 下 phases
 * - GET    /api/v1/phases/{id}             详情
 * - POST   /api/v1/phases                  新建（body 含 projectId）
 * - PATCH  /api/v1/phases/{id}             部分更新
 * - DELETE /api/v1/phases/{id}             真删（CASCADE 清 task）
 *
 * 跨级权限：所有读写都校验 phase.projectId 是否归属当前 userId
 */
@RestController
@RequestMapping("/api/v1/phases")
class PhaseController(
    private val phaseService: PhaseService,
) {

    @GetMapping
    fun listByProject(
        @RequestParam projectId: String,
        @RequestHeader("X-User-Id") userId: String,
    ): Map<String, Any?> = mapOf(
        "items" to phaseService.listByProject(projectId, userId).map(::toDto)
    )

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val phase = phaseService.get(id, userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(phase))
    }

    @PostMapping
    fun create(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: CreateRequest,
    ): ResponseEntity<Any> = when (
        val result = phaseService.create(
            projectId = req.projectId,
            userId = userId,
            name = req.name,
            description = req.description,
            sortOrder = req.sortOrder ?: 0,
            startDate = req.startDate,
            endDate = req.endDate,
        )
    ) {
        is PhaseService.CreateResult.Ok ->
            ResponseEntity.status(HttpStatus.CREATED).body(toDto(result.phase))
        PhaseService.CreateResult.ProjectNotFound ->
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "project_not_found"))
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: UpdateRequest,
    ): ResponseEntity<Any> = when (
        val result = phaseService.update(
            id = id,
            userId = userId,
            name = req.name,
            description = req.description,
            sortOrder = req.sortOrder,
            startDate = req.startDate,
            endDate = req.endDate,
            status = req.status,
            baseVersion = req.baseVersion,
        )
    ) {
        is PhaseService.UpdateResult.Ok -> ResponseEntity.ok(toDto(result.phase))
        PhaseService.UpdateResult.NotFound -> ResponseEntity.notFound().build()
        is PhaseService.UpdateResult.VersionConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(
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
    ): ResponseEntity<Any> = if (phaseService.delete(id, userId)) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.notFound().build()
    }

    private fun toDto(p: Phase) = mapOf(
        "id" to p.id,
        "projectId" to p.projectId,
        "name" to p.name,
        "description" to p.description,
        "sortOrder" to p.sortOrder,
        "startDate" to p.startDate?.toString(),
        "endDate" to p.endDate?.toString(),
        "status" to p.status,
        "version" to p.version,
        "createdAt" to p.createdAt?.toString(),
        "updatedAt" to p.updatedAt?.toString(),
    )

    data class CreateRequest(
        val projectId: String,
        val name: String,
        val description: String? = null,
        val sortOrder: Int? = null,
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null,
    )

    data class UpdateRequest(
        val name: String? = null,
        val description: String? = null,
        val sortOrder: Int? = null,
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null,
        val status: String? = null,
        val baseVersion: Long? = null,
    )
}
