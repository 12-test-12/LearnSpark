package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * R2：用户 AI 配置（按文档 §11.2.4：user_ai_configs）。
 *
 * apiKeyCipher 存储 AES-256-GCM 加密后的 base64 字符串：
 *   前 12 字节 = IV
 *   后续 16 字节 = GCM auth tag + ciphertext
 *   整体 base64 编码
 *
 * 客户端永远拿不到明文 API key：GET 响应只返回 mask（"sk-***last4"）。
 */
@Entity
@Table(name = "user_ai_configs")
class UserAiConfig(
    @Id
    var id: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(nullable = false)
    var provider: String = "deepseek",

    @Column(name = "api_key_cipher", nullable = false, columnDefinition = "TEXT")
    var apiKeyCipher: String = "",

    @Column(nullable = false)
    var model: String = "deepseek-chat",

    @Column(name = "base_url")
    var baseUrl: String? = null,

    @Column(name = "max_tokens", nullable = false)
    var maxTokens: Int = 2048,

    @Column(nullable = false)
    var temperature: Double = 0.7,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
)
