package com.learnspark.data.model

/**
 * R4a：用户已配置的 AI 通道（来自 GET /api/v1/ai/configs）。
 */
data class AiConfigDto(
    val id: String,
    val userId: String,
    val provider: String,
    val apiKeyMasked: String,        // 脱敏：***last4
    val model: String,
    val baseUrl: String?,
    val maxTokens: Int,
    val temperature: Double,
    val enabled: Boolean,
    val version: Long,
)

/**
 * R4c：任务上传文件。
 */
data class TaskUploadDto(
    val id: String,
    val taskId: String,
    val knowledgeEntryId: String?,
    val folderId: String?,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val uploadStatus: String,        // pending / parsing / ready / failed
    val parseError: String? = null,
)

/**
 * R4c：AI 标注的可参考文章。
 */
data class TaskArticleLinkDto(
    val id: String,
    val taskId: String,
    val entryId: String,
    val reason: String,
    val relevance: Int,              // 0-100
    val source: String,              // "ai" / "manual"
)
