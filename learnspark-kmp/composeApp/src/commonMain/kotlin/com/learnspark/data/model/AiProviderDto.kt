package com.learnspark.data.model

/**
 * R4a：AI provider 元数据（来自 GET /api/v1/ai/providers）。
 */
data class AiProviderDto(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val popularModels: List<String>,
    val apiKeyHint: String,
    val docsUrl: String? = null,
)
