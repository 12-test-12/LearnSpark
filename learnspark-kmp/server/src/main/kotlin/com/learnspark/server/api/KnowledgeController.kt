package com.learnspark.server.api

import com.learnspark.server.domain.entity.KnowledgeEntry
import com.learnspark.server.service.KnowledgeService
import org.springframework.data.domain.PageRequest
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
 * 阶段 2.4：知识库 CRUD API。
 *
 * - GET    /api/v1/knowledge?page=&size=        分页列表
 * - GET    /api/v1/knowledge/{id}              详情
 * - POST   /api/v1/knowledge                   创建（手动）
 * - PATCH  /api/v1/knowledge/{id}              更新（带 baseVersion 乐观锁）
 * - DELETE /api/v1/knowledge/{id}              软删除
 *
 * 当前 userId 取自 X-User-Id header（简化认证；阶段 1.2 JWT 接入后改成 token 解析）。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
class KnowledgeController(
    private val knowledgeService: KnowledgeService,
) {

    @GetMapping
    fun list(
        @RequestHeader("X-User-Id") userId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) q: String?,
    ): Map<String, Any?> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        val result = if (!q.isNullOrBlank()) {
            // 阶段 2.4：全文检索
            knowledgeService.search(userId, q, pageable)
        } else {
            knowledgeService.listByTag(userId, tag, pageable)
        }
        return mapOf(
            "items" to result.content.map(::toDto),
            "page" to result.number,
            "size" to result.size,
            "total" to result.totalElements,
            "totalPages" to result.totalPages,
            "query" to (q ?: ""),
            "tag" to (tag ?: ""),
        )
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Any> {
        val entry = knowledgeService.get(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(entry))
    }

    /**
     * 阶段 2.4：知识条目解析状态查询。
     * 客户端在上传文件后可轮询该端点，确认 PENDING → READY/FAILED 转换。
     */
    @GetMapping("/{id}/parse-status")
    fun parseStatus(@PathVariable id: String): ResponseEntity<Any> {
        val entry = knowledgeService.get(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "id" to entry.id,
                "parseStatus" to entry.parseStatus.name,
                "parseError" to entry.parseError,
                "hasContent" to !entry.content.isNullOrBlank(),
                "contentLength" to (entry.content?.length ?: 0),
            )
        )
    }

    @PostMapping
    fun create(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: CreateRequest,
    ): ResponseEntity<Any> {
        val entry = knowledgeService.create(
            userId = userId,
            title = req.title,
            content = req.content,
            tags = req.tags,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(entry))
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody req: UpdateRequest,
    ): ResponseEntity<Any> {
        return when (val result = knowledgeService.update(id, req.title, req.content, req.tags, req.baseVersion)) {
            is KnowledgeService.UpdateResult.Ok -> ResponseEntity.ok(toDto(result.entry))
            KnowledgeService.UpdateResult.NotFound -> ResponseEntity.notFound().build()
            is KnowledgeService.UpdateResult.VersionConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "error" to "version_conflict",
                    "currentVersion" to result.currentVersion,
                    "current" to toDto(result.current),
                )
            )
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Any> {
        val ok = knowledgeService.delete(id)
        return if (ok) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    private fun toDto(e: KnowledgeEntry) = mapOf(
        "id" to e.id,
        "userId" to e.userId,
        "folderId" to e.folderId,
        "title" to e.title,
        "content" to e.content,
        "sourceType" to e.sourceType.name,
        "fileSize" to e.fileSize,
        "fileType" to e.fileType,
        "parseStatus" to e.parseStatus.name,
        "parseError" to e.parseError,
        "tags" to e.tags,
        "version" to e.version,
        "createdAt" to e.createdAt?.toString(),
        "updatedAt" to e.updatedAt?.toString(),
    )

    data class CreateRequest(
        val title: String,
        val content: String? = null,
        val tags: String? = null,
    )

    data class UpdateRequest(
        val title: String? = null,
        val content: String? = null,
        val tags: String? = null,
        val baseVersion: Long? = null,
    )
}
