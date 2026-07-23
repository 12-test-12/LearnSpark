package com.learnspark.server.api

import com.learnspark.server.service.ai.TaskArticleSuggestionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * R4c：AI 标注可参考文章。
 *
 * - POST /api/v1/tasks/{taskId}/article-links/suggest
 *     body: { provider?: "deepseek"|"openai"|... }
 *     → AI 扫描任务上下文 + 知识库，top-K 写回 task_article_links
 * - GET  /api/v1/tasks/{taskId}/article-links
 *     → 列出该 task 的全部关联
 * - DELETE /api/v1/tasks/{taskId}/article-links/{entryId}
 * - POST /api/v1/tasks/{taskId}/article-links
 *     body: { entryId, reason }
 *     → 手动添加（不调 AI）
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/article-links")
class TaskArticleLinkController(
    private val service: TaskArticleSuggestionService,
) {

    @PostMapping("/suggest")
    fun suggest(
        @PathVariable taskId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody(required = false) req: SuggestRequest?,
    ): ResponseEntity<Any> = when (val r = service.suggest(userId, taskId, req?.provider)) {
        is TaskArticleSuggestionService.SuggestResult.Ok -> ResponseEntity.ok(mapOf("applied" to r.applied))
        TaskArticleSuggestionService.SuggestResult.TaskNotFound -> ResponseEntity.notFound().build()
        TaskArticleSuggestionService.SuggestResult.RateLimited -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(mapOf("error" to "rate_limited"))
        TaskArticleSuggestionService.SuggestResult.NoAiConfig -> ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
            .body(mapOf("error" to "no_ai_config"))
        TaskArticleSuggestionService.SuggestResult.EmptyContext -> ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
            .body(mapOf("error" to "empty_context"))
        TaskArticleSuggestionService.SuggestResult.NoCandidates -> ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
            .body(mapOf("error" to "no_candidates"))
        is TaskArticleSuggestionService.SuggestResult.AiError -> ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(mapOf("error" to "ai_error", "reason" to r.reason))
    }

    @GetMapping
    fun list(
        @PathVariable taskId: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val items = service.listLinks(userId, taskId)
        return ResponseEntity.ok(mapOf("items" to items.map(::toDto)))
    }

    @DeleteMapping("/{entryId}")
    fun delete(
        @PathVariable taskId: String,
        @PathVariable entryId: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> = if (service.deleteLink(userId, taskId, entryId)) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.notFound().build()
    }

    @PostMapping
    fun upsertManual(
        @PathVariable taskId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: ManualRequest,
    ): ResponseEntity<Any> = when (val r = service.upsertManualLink(userId, taskId, req.entryId, req.reason)) {
        TaskArticleSuggestionService.UpsertResult.Ok -> ResponseEntity.status(HttpStatus.CREATED)
            .body(mapOf("ok" to true))
        TaskArticleSuggestionService.UpsertResult.TaskNotFound -> ResponseEntity.notFound().build()
        TaskArticleSuggestionService.UpsertResult.EntryNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to "entry_not_found"))
    }

    private fun toDto(l: com.learnspark.server.domain.entity.TaskArticleLink) = mapOf(
        "id" to l.id,
        "taskId" to l.taskId,
        "userId" to l.userId,
        "entryId" to l.entryId,
        "reason" to l.reason,
        "relevance" to l.relevance,
        "source" to l.source,
        "version" to l.version,
        "createdAt" to l.createdAt?.toString(),
        "updatedAt" to l.updatedAt?.toString(),
    )

    data class SuggestRequest(val provider: String? = null)
    data class ManualRequest(val entryId: String, val reason: String = "")
}
