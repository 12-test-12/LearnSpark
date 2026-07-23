package com.learnspark.server.service.sync

import com.learnspark.server.api.SyncController
import com.learnspark.server.domain.entity.Phase
import com.learnspark.server.domain.repository.PhaseRepository
import com.learnspark.server.domain.repository.ProjectRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * R1：Phase 表同步 handler。
 *
 * 跨级 ownership 校验：
 * - phase 必须挂在 userId 拥有的 project 下
 * - 通过 ProjectRepository.existsByIdAndUserId 校验，避免加载整个 project
 */
@Component
class PhaseSyncHandler(
    private val phaseRepository: PhaseRepository,
    private val projectRepository: ProjectRepository,
) : SyncTableHandler {
    override val tableName: String = "phases"

    override fun apply(userId: String, change: SyncController.Change): SyncController.UploadResult {
        val payloadProjectId = change.payload["projectId"] as? String
        if (payloadProjectId == null) {
            return SyncController.UploadResult(change.id, status = "bad_request", serverVersion = 0L)
        }
        if (!projectRepository.existsByIdAndUserId(payloadProjectId, userId)) {
            return SyncController.UploadResult(change.id, status = "forbidden", serverVersion = 0L)
        }
        val existing = phaseRepository.findById(change.id).orElse(null)
        return if (existing == null) {
            if (change.baseVersion != 0L) {
                return SyncController.UploadResult(change.id, status = "conflict", serverVersion = 0L)
            }
            phaseRepository.save(toEntity(change, version = 1L))
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
            val merged = toEntity(change, version = existing.version + 1)
            merged.id = existing.id
            merged.createdAt = existing.createdAt
            merged.updatedAt = existing.updatedAt
            phaseRepository.save(merged)
            SyncController.UploadResult(change.id, status = "ok", serverVersion = existing.version + 1)
        }
    }

    override fun toPayload(entity: Any): Map<String, Any?> = toPayload(entity as Phase)

    fun toPayload(p: Phase): Map<String, Any?> = mapOf(
        "id" to p.id,
        "projectId" to p.projectId,
        "name" to p.name,
        "description" to p.description,
        "sortOrder" to p.sortOrder,
        "startDate" to p.startDate?.toString(),
        "endDate" to p.endDate?.toString(),
        "status" to p.status,
        "version" to p.version,
        "createdAt" to p.createdAt?.toString(),
        "updatedAt" to p.updatedAt?.toString(),
    )

    private fun toEntity(change: SyncController.Change, version: Long): Phase = Phase(
        id = change.id,
        projectId = (change.payload["projectId"] as? String) ?: "",
        name = (change.payload["name"] as? String) ?: "",
        description = change.payload["description"] as? String,
        sortOrder = (change.payload["sortOrder"] as? Number)?.toInt() ?: 0,
        startDate = (change.payload["startDate"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        endDate = (change.payload["endDate"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        status = (change.payload["status"] as? String) ?: "pending",
        version = version,
    )
}
