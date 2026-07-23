package com.learnspark.server.service.sync

import com.learnspark.server.api.SyncController
import com.learnspark.server.domain.entity.Project
import com.learnspark.server.domain.repository.ProjectRepository
import org.springframework.stereotype.Component

/**
 * R1：Project 表同步 handler。
 *
 * 与 SyncController.handleKnowledge 同结构：
 * 1) 校验 ownership（change.payload.userId == header）
 * 2) 服务端无记录 → 插入（baseVersion 必须为 0）
 * 3) 服务端有记录 → baseVersion 一致则写+1，否则 409 conflict
 */
@Component
class ProjectSyncHandler(
    private val repository: ProjectRepository,
) : SyncTableHandler {
    override val tableName: String = "projects"

    override fun apply(userId: String, change: SyncController.Change): SyncController.UploadResult {
        val payloadUserId = change.payload["userId"] as? String
        if (payloadUserId != userId) {
            return SyncController.UploadResult(change.id, status = "forbidden", serverVersion = 0L)
        }
        val existing = repository.findById(change.id).orElse(null)
        return if (existing == null) {
            if (change.baseVersion != 0L) {
                return SyncController.UploadResult(change.id, status = "conflict", serverVersion = 0L)
            }
            val entity = toEntity(change, userId, version = 1L)
            repository.save(entity)
            SyncController.UploadResult(change.id, status = "ok", serverVersion = 1L)
        } else {
            if (change.baseVersion != existing.version) {
                return SyncController.UploadResult(
                    id = change.id,
                    status = "conflict",
                    serverVersion = existing.version,
                    latest = toPayload(existing),
                )
            }
            val newVersion = existing.version + 1
            val merged = toEntity(change, userId, version = newVersion)
            merged.id = existing.id
            merged.createdAt = existing.createdAt
            merged.updatedAt = existing.updatedAt
            repository.save(merged)
            SyncController.UploadResult(change.id, status = "ok", serverVersion = newVersion)
        }
    }

    override fun toPayload(entity: Any): Map<String, Any?> = toPayload(entity as Project)

    fun toPayload(p: Project): Map<String, Any?> = mapOf(
        "id" to p.id,
        "userId" to p.userId,
        "name" to p.name,
        "description" to p.description,
        "goal" to p.goal,
        "coverColor" to p.coverColor,
        "dailyHours" to p.dailyHours,
        "isAiGenerated" to p.isAiGenerated,
        "status" to p.status,
        "version" to p.version,
        "createdAt" to p.createdAt?.toString(),
        "updatedAt" to p.updatedAt?.toString(),
    )

    private fun toEntity(change: SyncController.Change, userId: String, version: Long): Project = Project(
        id = change.id,
        userId = userId,
        name = (change.payload["name"] as? String) ?: "",
        description = change.payload["description"] as? String,
        goal = change.payload["goal"] as? String,
        coverColor = change.payload["coverColor"] as? String,
        dailyHours = (change.payload["dailyHours"] as? Number)?.toInt() ?: 2,
        isAiGenerated = (change.payload["isAiGenerated"] as? Boolean) ?: false,
        status = (change.payload["status"] as? String) ?: "active",
        version = version,
    )
}
