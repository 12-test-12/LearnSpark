package com.learnspark.server.service.sync

import com.learnspark.server.domain.entity.KnowledgeEntry
import com.learnspark.server.domain.repository.KnowledgeEntryRepository
import com.learnspark.server.domain.repository.KnowledgeFolderRepository
import org.springframework.stereotype.Component

/**
 * R1：KnowledgeEntry 表同步 handler。
 *
 * 由原 SyncController.handleKnowledge 提取而来（消除重复代码）。
 */
@Component
class KnowledgeSyncHandler(
    private val repository: KnowledgeEntryRepository,
    private val folderRepository: KnowledgeFolderRepository,
) : SyncTableHandler {
    override val tableName: String = "knowledge_entries"

    override fun apply(userId: String, change: com.learnspark.server.api.SyncController.Change):
        com.learnspark.server.api.SyncController.UploadResult {
        val payloadUserId = change.payload["userId"] as? String
        if (payloadUserId != userId) {
            return com.learnspark.server.api.SyncController.UploadResult(
                change.id, status = "forbidden", serverVersion = 0L
            )
        }
        // 校验 folderId 属于同一 user
        val folderId = change.payload["folderId"] as? String
        if (folderId != null && folderRepository.findByIdAndUserId(folderId, userId) == null) {
            return com.learnspark.server.api.SyncController.UploadResult(
                change.id, status = "forbidden", serverVersion = 0L
            )
        }
        val existing = repository.findById(change.id).orElse(null)
        return if (existing == null) {
            if (change.baseVersion != 0L) {
                return com.learnspark.server.api.SyncController.UploadResult(
                    change.id, status = "conflict", serverVersion = 0L
                )
            }
            repository.save(toEntity(change, userId, version = 1L))
            com.learnspark.server.api.SyncController.UploadResult(
                change.id, status = "ok", serverVersion = 1L
            )
        } else {
            if (existing.userId != userId) {
                return com.learnspark.server.api.SyncController.UploadResult(
                    change.id, status = "forbidden", serverVersion = 0L
                )
            }
            if (change.baseVersion != existing.version) {
                return com.learnspark.server.api.SyncController.UploadResult(
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
            com.learnspark.server.api.SyncController.UploadResult(
                change.id, status = "ok", serverVersion = existing.version + 1
            )
        }
    }

    override fun toPayload(entity: Any): Map<String, Any?> = toPayload(entity as KnowledgeEntry)

    fun toPayload(e: KnowledgeEntry): Map<String, Any?> = mapOf(
        "id" to e.id,
        "userId" to e.userId,
        "folderId" to e.folderId,
        "title" to e.title,
        "content" to e.content,
        "sourceType" to e.sourceType.name,
        "sourcePath" to e.sourcePath,
        "fileSize" to e.fileSize,
        "fileType" to e.fileType,
        "parseStatus" to e.parseStatus.name,
        "tags" to e.tags,
        "version" to e.version,
        "createdAt" to e.createdAt?.toString(),
        "updatedAt" to e.updatedAt?.toString(),
    )

    private fun toEntity(change: com.learnspark.server.api.SyncController.Change, userId: String, version: Long): KnowledgeEntry {
        val resolvedType = (change.payload["sourceType"] as? String)
            ?.let { runCatching { KnowledgeEntry.SourceType.valueOf(it) }.getOrNull() }
            ?: KnowledgeEntry.SourceType.MANUAL
        return KnowledgeEntry(
            id = change.id,
            userId = userId,
            folderId = change.payload["folderId"] as? String,
            title = (change.payload["title"] as? String) ?: "",
            content = change.payload["content"] as? String,
            sourceType = resolvedType,
            sourcePath = change.payload["sourcePath"] as? String,
            fileSize = (change.payload["fileSize"] as? Number)?.toLong(),
            fileType = change.payload["fileType"] as? String,
            parseStatus = KnowledgeEntry.ParseStatus.READY,
            tags = change.payload["tags"] as? String,
            version = version,
        )
    }
}
