package com.learnspark.server.service.sync

import com.learnspark.server.api.SyncController
import com.learnspark.server.domain.entity.KnowledgeFolder
import com.learnspark.server.domain.repository.KnowledgeFolderRepository
import org.springframework.stereotype.Component

/**
 * R3a：KnowledgeFolder 表同步 handler。
 *
 * 上传时如果 parentId 跨用户，则 forbidden（防止构造非法归属）。
 */
@Component
class KnowledgeFolderSyncHandler(
    private val repository: KnowledgeFolderRepository,
) : SyncTableHandler {
    override val tableName: String = "knowledge_folders"

    override fun apply(userId: String, change: SyncController.Change): SyncController.UploadResult {
        val parentId = change.payload["parentId"] as? String
        if (parentId != null && repository.findByIdAndUserId(parentId, userId) == null) {
            return SyncController.UploadResult(change.id, status = "forbidden", serverVersion = 0L)
        }
        val existing = repository.findById(change.id).orElse(null)
        return if (existing == null) {
            if (change.baseVersion != 0L) {
                return SyncController.UploadResult(change.id, status = "conflict", serverVersion = 0L)
            }
            repository.save(toEntity(change, userId, version = 1L))
            SyncController.UploadResult(change.id, status = "ok", serverVersion = 1L)
        } else {
            if (existing.userId != userId) {
                return SyncController.UploadResult(change.id, status = "forbidden", serverVersion = 0L)
            }
            if (change.baseVersion != existing.version) {
                return SyncController.UploadResult(
                    id = change.id,
                    status = "conflict",
                    serverVersion = existing.version,
                    latest = toPayload(existing),
                )
            }
            val merged = toEntity(change, userId, version = existing.version + 1)
            merged.id = existing.id
            merged.createdAt = existing.createdAt
            merged.updatedAt = existing.updatedAt
            repository.save(merged)
            SyncController.UploadResult(change.id, status = "ok", serverVersion = existing.version + 1)
        }
    }

    override fun toPayload(entity: Any): Map<String, Any?> = toPayload(entity as KnowledgeFolder)

    fun toPayload(f: KnowledgeFolder): Map<String, Any?> = mapOf(
        "id" to f.id,
        "userId" to f.userId,
        "parentId" to f.parentId,
        "name" to f.name,
        "color" to f.color,
        "icon" to f.icon,
        "sortOrder" to f.sortOrder,
        "path" to f.path,
        "depth" to f.depth,
        "version" to f.version,
        "createdAt" to f.createdAt?.toString(),
        "updatedAt" to f.updatedAt?.toString(),
    )

    private fun toEntity(change: SyncController.Change, userId: String, version: Long): KnowledgeFolder = KnowledgeFolder(
        id = change.id,
        userId = userId,
        parentId = change.payload["parentId"] as? String,
        name = (change.payload["name"] as? String) ?: "",
        color = change.payload["color"] as? String,
        icon = change.payload["icon"] as? String,
        sortOrder = (change.payload["sortOrder"] as? Number)?.toInt() ?: 0,
        path = (change.payload["path"] as? String) ?: "/",
        depth = (change.payload["depth"] as? Number)?.toInt() ?: 0,
        version = version,
    )
}
