package com.learnspark.data.model

import kotlinx.serialization.Serializable

/**
 * R3a：知识库文件夹 DTO。
 *
 * 纯 Kotlin DTO，跨端通用；服务端 → 客户端的 payload 用 Map<String,Any?> 解析后转此结构。
 */
@Serializable
data class KnowledgeFolderDto(
    val id: String,
    val userId: String,
    val parentId: String? = null,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val path: String = "/",
    val depth: Int = 0,
    val version: Long = 1L,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): KnowledgeFolderDto = KnowledgeFolderDto(
            id = map["id"] as? String ?: "",
            userId = map["userId"] as? String ?: "",
            parentId = map["parentId"] as? String,
            name = map["name"] as? String ?: "",
            color = map["color"] as? String,
            icon = map["icon"] as? String,
            sortOrder = (map["sortOrder"] as? Number)?.toInt() ?: 0,
            path = map["path"] as? String ?: "/",
            depth = (map["depth"] as? Number)?.toInt() ?: 0,
            version = (map["version"] as? Number)?.toLong() ?: 1L,
            createdAt = map["createdAt"] as? String,
            updatedAt = map["updatedAt"] as? String,
        )
    }
}
