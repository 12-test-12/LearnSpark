package com.learnspark.server.api

import com.learnspark.server.domain.entity.KnowledgeFolder
import com.learnspark.server.service.KnowledgeFolderService
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
 * R3a：知识库文件夹 REST API。
 *
 * - GET    /api/v1/knowledge/folders?parentId=...    列子目录（parentId 缺省=根）
 * - GET    /api/v1/knowledge/folders/tree            列全树（扁平按 path 排序）
 * - GET    /api/v1/knowledge/folders/{id}            详情
 * - POST   /api/v1/knowledge/folders                 新建
 * - PATCH  /api/v1/knowledge/folders/{id}            改名/颜色/排序
 * - POST   /api/v1/knowledge/folders/{id}/move       移动到新父
 * - DELETE /api/v1/knowledge/folders/{id}            删除（条目 folderId 置 null）
 */
@RestController
@RequestMapping("/api/v1/knowledge/folders")
class KnowledgeFolderController(
    private val service: KnowledgeFolderService,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) parentId: String?,
        @RequestHeader("X-User-Id") userId: String,
    ): Map<String, Any?> {
        val items = if (parentId == null) {
            service.list(userId)
        } else {
            service.listByParent(userId, parentId)
        }
        return mapOf("items" to items.map(::toDto))
    }

    @GetMapping("/tree")
    fun tree(@RequestHeader("X-User-Id") userId: String): Map<String, Any?> =
        mapOf("items" to service.list(userId).map(::toDto))

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val f = service.get(id, userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(f))
    }

    @PostMapping
    fun create(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: CreateRequest,
    ): ResponseEntity<Any> = when (
        val r = service.create(
            userId = userId,
            name = req.name,
            parentId = req.parentId,
            color = req.color,
            icon = req.icon,
            sortOrder = req.sortOrder ?: 0,
        )
    ) {
        is KnowledgeFolderService.CreateResult.Ok ->
            ResponseEntity.status(HttpStatus.CREATED).body(toDto(r.folder))
        KnowledgeFolderService.CreateResult.InvalidName ->
            ResponseEntity.badRequest().body(mapOf("error" to "invalid_name"))
        KnowledgeFolderService.CreateResult.ParentNotFound ->
            ResponseEntity.badRequest().body(mapOf("error" to "parent_not_found"))
        KnowledgeFolderService.CreateResult.DepthExceeded ->
            ResponseEntity.badRequest().body(mapOf("error" to "depth_exceeded", "max" to KnowledgeFolder.MAX_DEPTH))
        KnowledgeFolderService.CreateResult.DuplicateName ->
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "duplicate_name_in_parent"))
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: UpdateRequest,
    ): ResponseEntity<Any> = when (
        val r = service.update(
            id = id,
            userId = userId,
            name = req.name,
            color = req.color,
            icon = req.icon,
            sortOrder = req.sortOrder,
        )
    ) {
        is KnowledgeFolderService.UpdateResult.Ok -> ResponseEntity.ok(toDto(r.folder))
        KnowledgeFolderService.UpdateResult.NotFound -> ResponseEntity.notFound().build()
        KnowledgeFolderService.UpdateResult.DuplicateName ->
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "duplicate_name_in_parent"))
    }

    @PostMapping("/{id}/move")
    fun move(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: MoveRequest,
    ): ResponseEntity<Any> = when (
        val r = service.move(id, userId, req.newParentId)
    ) {
        is KnowledgeFolderService.MoveResult.Ok -> ResponseEntity.ok(toDto(r.folder))
        KnowledgeFolderService.MoveResult.NotFound ->
            ResponseEntity.notFound().build()
        KnowledgeFolderService.MoveResult.ParentNotFound ->
            ResponseEntity.badRequest().body(mapOf("error" to "parent_not_found"))
        KnowledgeFolderService.MoveResult.CycleDetected ->
            ResponseEntity.badRequest().body(mapOf("error" to "cycle_detected"))
        KnowledgeFolderService.MoveResult.DepthExceeded ->
            ResponseEntity.badRequest().body(mapOf("error" to "depth_exceeded", "max" to KnowledgeFolder.MAX_DEPTH))
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

    private fun toDto(f: KnowledgeFolder) = mapOf(
        "id" to f.id,
        "userId" to f.userId,
        "parentId" to f.parentId,
        "name" to f.name,
        "color" to f.color,
        "icon" to f.icon,
        "sortOrder" to f.sortOrder,
        "path" to f.path,
        "depth" to f.depth,
        "version" to f.version,
        "createdAt" to f.createdAt?.toString(),
        "updatedAt" to f.updatedAt?.toString(),
    )

    data class CreateRequest(
        val name: String,
        val parentId: String? = null,
        val color: String? = null,
        val icon: String? = null,
        val sortOrder: Int? = null,
    )

    data class UpdateRequest(
        val name: String? = null,
        val color: String? = null,
        val icon: String? = null,
        val sortOrder: Int? = null,
    )

    data class MoveRequest(val newParentId: String?)
}
