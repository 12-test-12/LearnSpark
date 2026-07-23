package com.learnspark.server.api

import com.learnspark.server.service.ai.KnowledgeOrganizeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * R3c：知识库 AI 整理端点。
 *
 * - POST /api/v1/knowledge/organize/suggest   entryIds=null → 全量最近30条
 * - POST /api/v1/knowledge/organize/apply     客户端确认后落库
 */
@RestController
@RequestMapping("/api/v1/knowledge/organize")
class OrganizeController(
    private val service: KnowledgeOrganizeService,
) {

    @PostMapping("/suggest")
    fun suggest(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: SuggestRequest,
    ): ResponseEntity<Any> = when (val r = service.suggest(userId, req.entryIds)) {
        is KnowledgeOrganizeService.OrganizeResult.Ok ->
            ResponseEntity.ok(mapOf("suggestions" to r.suggestions))
        KnowledgeOrganizeService.OrganizeResult.RateLimited ->
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(mapOf("error" to "rate_limited"))
        KnowledgeOrganizeService.OrganizeResult.NoAiConfig ->
            ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(mapOf("error" to "no_ai_config"))
        KnowledgeOrganizeService.OrganizeResult.NoFolders ->
            ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(mapOf("error" to "no_folders"))
        KnowledgeOrganizeService.OrganizeResult.NoEntries ->
            ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(mapOf("error" to "no_entries"))
        is KnowledgeOrganizeService.OrganizeResult.AiError ->
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(mapOf("error" to "ai_error", "reason" to r.reason))
    }

    @PostMapping("/apply")
    fun apply(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: ApplyRequest,
    ): ResponseEntity<Any> {
        val mapped = req.acceptances.map { KnowledgeOrganizeService.Acceptance(it.entryId, it.folderId) }
        val r = service.apply(userId, mapped)
        return ResponseEntity.ok(mapOf("applied" to r.applied, "failed" to r.failed))
    }

    data class SuggestRequest(val entryIds: List<String>?)
    data class ApplyRequest(val acceptances: List<Acceptance>)

    data class Acceptance(
        val entryId: String,
        val folderId: String?,
    )
}
