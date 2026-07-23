package com.learnspark.server.service.ai

/**
 * R4a：AI provider 元数据。
 *
 * 描述一个 AI 服务商的连接参数 + 默认模型列表。
 * provider id 同时也是 user_ai_configs.provider 字段的取值。
 */
data class AiProvider(
    val id: String,                 // 唯一 id（同时存 DB）：deepseek / openai / qwen / glm / moonshot / custom
    val displayName: String,         // 用户可见名："深度求索 DeepSeek"
    val defaultBaseUrl: String,      // 默认 API 端点
    val defaultModel: String,        // 默认模型
    val popularModels: List<String>, // 常见模型下拉列表
    val apiKeyHint: String,          // 输入框提示：例如 "sk-..."
    val docsUrl: String? = null,     // 文档链接（UI 跳转）
)
