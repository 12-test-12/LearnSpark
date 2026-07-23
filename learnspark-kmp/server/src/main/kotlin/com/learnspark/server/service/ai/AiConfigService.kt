package com.learnspark.server.service.ai

import com.learnspark.server.common.crypto.AesCryptoService
import com.learnspark.server.common.ratelimit.UserRateLimiter
import com.learnspark.server.domain.entity.UserAiConfig
import com.learnspark.server.domain.repository.UserAiConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * R2：用户 AI 配置服务。
 *
 * - apiKey 入参明文 → AES 加密存库
 * - apiKey 出参永远脱敏（mask）
 * - testConnection 走 RateLimiter（5 次/分钟）
 *
 * 与 SubmissionService 的关系：
 * SubmissionService 通过 [resolveApiKey] 获取用户的明文 API key，
 * 再调用 DeepSeekClient；本服务不直接调用 DeepSeek。
 */
@Service
class AiConfigService(
    private val repository: UserAiConfigRepository,
    private val crypto: AesCryptoService,
    private val rateLimiter: UserRateLimiter,
    private val aiClient: OpenAICompatibleClient,
    private val providerRegistry: AiProviderRegistry,
) {
    private val log = LoggerFactory.getLogger(AiConfigService::class.java)

    fun listProviders(): List<AiProvider> = providerRegistry.list()

    fun list(userId: String): List<AiConfigDto> =
        repository.findByUserId(userId).map { toDto(it) }

    fun get(id: String, userId: String): AiConfigDto? {
        val c = repository.findById(id).orElse(null) ?: return null
        if (c.userId != userId) return null
        return toDto(c)
    }

    @Transactional
    fun upsert(
        userId: String,
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String? = null,
        maxTokens: Int = 2048,
        temperature: Double = 0.7,
        enabled: Boolean = true,
    ): AiConfigDto {
        // 同 user+provider 已存在 → 覆盖（保留原 id 与 version+1）
        val existing = repository.findByUserIdAndProvider(userId, provider)
        val saved = if (existing == null) {
            val c = UserAiConfig(
                id = UUID.randomUUID().toString(),
                userId = userId,
                provider = provider,
                apiKeyCipher = crypto.encrypt(apiKey),
                model = model,
                baseUrl = baseUrl,
                maxTokens = maxTokens.coerceIn(64, 32768),
                temperature = temperature.coerceIn(0.0, 2.0),
                enabled = enabled,
                version = 1L,
            )
            repository.save(c)
        } else {
            existing.apiKeyCipher = crypto.encrypt(apiKey)
            existing.model = model
            existing.baseUrl = baseUrl
            existing.maxTokens = maxTokens.coerceIn(64, 32768)
            existing.temperature = temperature.coerceIn(0.0, 2.0)
            existing.enabled = enabled
            existing.version = existing.version + 1
            repository.save(existing)
        }
        log.info("AiConfig upsert user={} provider={} version={}", userId, provider, saved.version)
        return toDto(saved)
    }

    @Transactional
    fun delete(id: String, userId: String): Boolean {
        val c = repository.findById(id).orElse(null) ?: return false
        if (c.userId != userId) return false
        repository.delete(c)
        return true
    }

    /**
     * 测试连通性：限流 5/min，发送 1 条 hello 消息，验证返回。
     */
    fun testConnection(id: String, userId: String): TestResult {
        if (!rateLimiter.tryAcquire(userId, permits = 1, perMinute = 5)) {
            return TestResult(false, "rate_limited", null)
        }
        val c = repository.findById(id).orElse(null) ?: return TestResult(false, "not_found", null)
        if (c.userId != userId) return TestResult(false, "not_found", null)
        val apiKey = crypto.decrypt(c.apiKeyCipher)
        val baseUrl = c.baseUrl ?: providerRegistry.get(c.provider)?.defaultBaseUrl
        return try {
            val resp = aiClient.chatCompletion(
                apiKey = apiKey,
                baseUrl = baseUrl,
                req = OpenAICompatibleClient.ChatRequest(
                    model = c.model,
                    messages = listOf(
                        OpenAICompatibleClient.Message("user", "ping")
                    ),
                    maxTokens = 8,
                ),
            )
            val reply = resp.choices.firstOrNull()?.message?.content
            TestResult(true, "ok", reply)
        } catch (e: OpenAICompatibleClient.AiClientException) {
            TestResult(false, "ai_error", e.message)
        }
    }

    /**
     * 给 SubmissionService / KnowledgeOrganizeService 等使用：
     * 解析用户启用配置的明文 API key。
     * @return null = 用户未配置或已禁用
     */
    fun resolveApiKey(userId: String, provider: String = "deepseek"): ResolvedConfig? {
        val c = repository.findByUserIdAndProvider(userId, provider) ?: return null
        if (!c.enabled) return null
        val baseUrl = c.baseUrl ?: providerRegistry.get(c.provider)?.defaultBaseUrl
        return ResolvedConfig(
            apiKey = crypto.decrypt(c.apiKeyCipher),
            model = c.model,
            baseUrl = baseUrl,
            maxTokens = c.maxTokens,
            temperature = c.temperature,
        )
    }

    // === DTOs / 输出 ===

    data class AiConfigDto(
        val id: String,
        val userId: String,
        val provider: String,
        val apiKeyMasked: String,   // 脱敏："sk-***last4"
        val model: String,
        val baseUrl: String?,
        val maxTokens: Int,
        val temperature: Double,
        val enabled: Boolean,
        val version: Long,
        val createdAt: String?,
        val updatedAt: String?,
    )

    data class TestResult(
        val success: Boolean,
        val reason: String,
        val sample: String?,
    )

    data class ResolvedConfig(
        val apiKey: String,
        val model: String,
        val baseUrl: String?,
        val maxTokens: Int,
        val temperature: Double,
    )

    private fun toDto(c: UserAiConfig): AiConfigDto = AiConfigDto(
        id = c.id,
        userId = c.userId,
        provider = c.provider,
        // 反脱敏：解密后只取末尾 4 位拼接 ***；绝不返回完整 key
        apiKeyMasked = "***" + runCatching {
            crypto.decrypt(c.apiKeyCipher).takeLast(4)
        }.getOrDefault("****"),
        model = c.model,
        baseUrl = c.baseUrl,
        maxTokens = c.maxTokens,
        temperature = c.temperature,
        enabled = c.enabled,
        version = c.version,
        createdAt = c.createdAt?.toString(),
        updatedAt = c.updatedAt?.toString(),
    )
}
