package com.learnspark.server.api

import com.learnspark.server.service.ai.AiConfigService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * R2：用户 AI 配置 REST API。
 *
 * - GET    /api/v1/ai/configs                   列出当前用户全部配置
 * - GET    /api/v1/ai/configs/{id}              详情（apiKey 脱敏）
 * - POST   /api/v1/ai/configs                   新建或更新（按 provider upsert）
 * - DELETE /api/v1/ai/configs/{id}              删除
 * - POST   /api/v1/ai/configs/{id}/test         测试连通性（限流 5/min）
 *
 * 安全：响应中的 apiKeyMasked 已脱敏为 "***last4"；原始明文永不返回。
 */
@RestController
@RequestMapping("/api/v1/ai/configs")
class AiConfigController(
    private val service: AiConfigService,
) {

    @GetMapping
    fun list(@RequestHeader("X-User-Id") userId: String): Map<String, Any?> =
        mapOf("items" to service.list(userId))

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val c = service.get(id, userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(c)
    }

    @PostMapping
    fun upsert(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: UpsertRequest,
    ): ResponseEntity<Any> {
        if (req.apiKey.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "api_key_required"))
        }
        if (req.provider.isBlank() || req.model.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "provider_and_model_required"))
        }
        val dto = service.upsert(
            userId = userId,
            provider = req.provider,
            apiKey = req.apiKey,
            model = req.model,
            baseUrl = req.baseUrl,
            maxTokens = req.maxTokens ?: 2048,
            temperature = req.temperature ?: 0.7,
            enabled = req.enabled ?: true,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(dto)
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

    @PostMapping("/{id}/test")
    fun testConnection(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val r = service.testConnection(id, userId)
        return when (r.reason) {
            "rate_limited" -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(mapOf("error" to "rate_limited", "limit" to "5/min"))
            "not_found" -> ResponseEntity.notFound().build()
            "ok" -> ResponseEntity.ok(mapOf("success" to true, "sample" to r.sample))
            else -> ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(mapOf("success" to false, "reason" to r.reason, "detail" to r.sample))
        }
    }

    data class UpsertRequest(
        val provider: String,
        val apiKey: String,
        val model: String,
        val baseUrl: String? = null,
        val maxTokens: Int? = null,
        val temperature: Double? = null,
        val enabled: Boolean? = null,
    )
}
