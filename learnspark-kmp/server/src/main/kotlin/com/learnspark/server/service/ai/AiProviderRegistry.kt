package com.learnspark.server.service.ai

import org.springframework.stereotype.Component

/**
 * R4a：AI provider 注册表。
 *
 * 内置 6 家 OpenAI 兼容协议的国内 / 国外服务商。
 * 新增 provider 只需在 [defaultProviders] 加一条；用户也可在 UI 选 "custom" 填自定义 baseUrl。
 */
@Component
class AiProviderRegistry {

    private val all: List<AiProvider> = defaultProviders()

    fun list(): List<AiProvider> = all

    fun get(id: String): AiProvider? = all.firstOrNull { it.id == id.lowercase() }

    private fun defaultProviders(): List<AiProvider> = listOf(
        AiProvider(
            id = "deepseek",
            displayName = "深度求索 DeepSeek",
            defaultBaseUrl = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
            popularModels = listOf("deepseek-chat", "deepseek-reasoner"),
            apiKeyHint = "sk-...",
            docsUrl = "https://platform.deepseek.com/",
        ),
        AiProvider(
            id = "openai",
            displayName = "OpenAI",
            defaultBaseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            popularModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo"),
            apiKeyHint = "sk-proj-...",
            docsUrl = "https://platform.openai.com/",
        ),
        AiProvider(
            id = "qwen",
            displayName = "通义千问 Qwen（DashScope）",
            defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-plus",
            popularModels = listOf("qwen-plus", "qwen-turbo", "qwen-max", "qwen-long"),
            apiKeyHint = "sk-...",
            docsUrl = "https://help.aliyun.com/zh/model-studio/",
        ),
        AiProvider(
            id = "glm",
            displayName = "智谱 GLM",
            defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-flash",
            popularModels = listOf("glm-4-flash", "glm-4-air", "glm-4", "glm-4-plus"),
            apiKeyHint = "...",
            docsUrl = "https://open.bigmodel.cn/",
        ),
        AiProvider(
            id = "moonshot",
            displayName = "月之暗面 Moonshot",
            defaultBaseUrl = "https://api.moonshot.cn/v1",
            defaultModel = "moonshot-v1-8k",
            popularModels = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k"),
            apiKeyHint = "sk-...",
            docsUrl = "https://platform.moonshot.cn/",
        ),
        AiProvider(
            id = "custom",
            displayName = "自定义（OpenAI 兼容协议）",
            defaultBaseUrl = "https://your-endpoint.example.com/v1",
            defaultModel = "your-model",
            popularModels = emptyList(),
            apiKeyHint = "your-api-key",
            docsUrl = null,
        ),
    )
}
