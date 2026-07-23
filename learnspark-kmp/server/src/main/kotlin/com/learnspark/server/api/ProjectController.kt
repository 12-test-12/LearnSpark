package com.learnspark.server.api

import com.learnspark.server.domain.entity.Project
import com.learnspark.server.service.ProjectService
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
import org.springframework.web.bind.annotation.RestController

/**
 * R1：Project REST API。
 *
 * 端点：
 * - GET    /api/v1/projects                    列出当前用户全部 project
 * - GET    /api/v1/projects/{id}               详情
 * - POST   /api/v1/projects                    新建
 * - PATCH  /api/v1/projects/{id}               部分更新（乐观锁）
 * - DELETE /api/v1/projects/{id}               真删（CASCADE 清理下属）
 *
 * userId 全部从 X-User-Id header 取（阶段 1.2 JWT 接入后切换到 token 解析）
 */
@RestController
@RequestMapping("/api/v1/projects")
class ProjectController(
    private val projectService: ProjectService,
) {

    @GetMapping
    fun list(@RequestHeader("X-User-Id") userId: String): Map<String, Any?> =
        mapOf("items" to projectService.list(userId).map(::toDto))

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val project = projectService.get(id, userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(project))
    }

    @PostMapping
    fun create(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: CreateRequest,
    ): ResponseEntity<Any> {
        if (req.name.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "name_required"))
        }
        val project = projectService.create(
            userId = userId,
            name = req.name,
            description = req.description,
            goal = req.goal,
            coverColor = req.coverColor,
            dailyHours = req.dailyHours ?: 2,
            isAiGenerated = req.isAiGenerated ?: false,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(project))
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: UpdateRequest,
    ): ResponseEntity<Any> = when (
        val result = projectService.update(
            id = id,
            userId = userId,
            name = req.name,
            description = req.description,
            goal = req.goal,
            coverColor = req.coverColor,
            dailyHours = req.dailyHours,
            status = req.status,
            baseVersion = req.baseVersion,
        )
    ) {
        is ProjectService.UpdateResult.Ok -> ResponseEntity.ok(toDto(result.project))
        ProjectService.UpdateResult.NotFound -> ResponseEntity.notFound().build()
        is ProjectService.UpdateResult.VersionConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(
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
    ): ResponseEntity<Any> {
        return if (projectService.delete(id, userId)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    }

    private fun toDto(p: Project) = mapOf(
        "id" to p.id,
        "userId" to p.userId,
        "name" to p.name,
        "description" to p.description,
        "goal" to p.goal,
        "coverColor" to p.coverColor,
        "dailyHours" to p.dailyHours,
        "isAiGenerated" to p.isAiGenerated,
        "status" to p.status,
        "version" to p.version,
        "createdAt" to p.createdAt?.toString(),
        "updatedAt" to p.updatedAt?.toString(),
    )

    data class CreateRequest(
        val name: String,
        val description: String? = null,
        val goal: String? = null,
        val coverColor: String? = null,
        val dailyHours: Int? = null,
        val isAiGenerated: Boolean? = null,
    )

    data class UpdateRequest(
        val name: String? = null,
        val description: String? = null,
        val goal: String? = null,
        val coverColor: String? = null,
        val dailyHours: Int? = null,
        val status: String? = null,
        val baseVersion: Long? = null,
    )
}
