package com.learnspark.data.model

/**
 * R5e：知识库条目（来自 GET /api/v1/knowledge）。
 *
 * 字段与 KnowledgeController.toDto 对齐。
 */
data class KnowledgeEntryDto(
    val id: String,
    val userId: String,
    val folderId: String? = null,
    val title: String,
    val content: String? = null,
    val sourceType: String = "FILE",     // MANUAL / FILE / LINK
    val fileSize: Long = 0,
    val fileType: String = "",           // 扩展名
    val parseStatus: String = "READY",   // READY / PENDING / PROCESSING / FAILED
    val parseError: String? = null,
    val tags: String? = null,
    val version: Long = 1,
) {
    val fileName: String
        get() = title

    companion object {
        fun fromMap(m: Map<String, Any?>): KnowledgeEntryDto = KnowledgeEntryDto(
            id = m["id"] as? String ?: "",
            userId = m["userId"] as? String ?: "",
            folderId = m["folderId"] as? String,
            title = m["title"] as? String ?: "",
            content = m["content"] as? String,
            sourceType = m["sourceType"] as? String ?: "FILE",
            fileSize = (m["fileSize"] as? Number)?.toLong() ?: 0,
            fileType = m["fileType"] as? String ?: "",
            parseStatus = m["parseStatus"] as? String ?: "READY",
            parseError = m["parseError"] as? String,
            tags = m["tags"] as? String,
            version = (m["version"] as? Number)?.toLong() ?: 1L,
        )
    }
}
