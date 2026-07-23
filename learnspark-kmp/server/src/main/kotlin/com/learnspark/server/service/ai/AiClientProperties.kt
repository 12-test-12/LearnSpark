package com.learnspark.server.service.ai

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * R4a：AI 客户端全局配置（替代 R2 的 DeepSeekProperties）。
 *
 * 优先级：环境变量 > application.yml
 *   LEARNSPARK_AI_DEFAULT_BASE_URL
 *   LEARNSPARK_AI_TIMEOUT_SECONDS
 *
 * 注：baseUrl 现在主要用作兜底；实际请求 baseUrl 来自 user_ai_configs.base_url 或
 * AiProviderRegistry 中该 provider 的 defaultBaseUrl。
 */
@ConfigurationProperties(prefix = "learnspark.ai")
data class AiClientProperties(
    var defaultBaseUrl: String = "https://api.deepseek.com/v1",
    var timeoutSeconds: Int = 30,
    var maxRetries: Int = 1,
)
