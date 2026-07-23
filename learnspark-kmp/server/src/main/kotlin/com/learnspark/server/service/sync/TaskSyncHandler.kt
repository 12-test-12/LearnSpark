package com.learnspark.server.service.sync

import com.learnspark.server.api.SyncController
import com.learnspark.server.domain.entity.Task
import com.learnspark.server.domain.repository.PhaseRepository
import com.learnspark.server.domain.repository.TaskRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * R1：Task 表同步 handler。
 *
 * 跨级 ownership 校验：
 * - task 必须挂在 userId 拥有的 project 下的 phase 内
 * - 经由 phase → project 反查，避免在 handler 内重复权限逻辑
 */
@Component
class TaskSyncHandler(
    private val taskRepository: TaskRepository,
    private val phaseRepository: PhaseRepository,
    private val projectRepository: com.learnspark.server.domain.repository.ProjectRepository,
) : SyncTableHandler {
    override val tableName: String = "tasks"

    override fun apply(userId: String, change: SyncController.Change): SyncController.UploadResult {
        val phaseId = change.payload["phaseId"] as? String
        if (phaseId == null) {
            return SyncController.UploadResult(change.id, status = "bad_request", serverVersion = 0L)
        }
        if (!phaseBelongsToUser(phaseId, userId)) {
            return SyncController.UploadResult(change.id, status = "forbidden", serverVersion = 0L)
        }
        val existing = taskRepository.findById(change.id).orElse(null)
        return if (existing == null) {
            if (change.baseVersion != 0L) {
                return SyncController.UploadResult(change.id, status = "conflict", serverVersion = 0L)
            }
            taskRepository.save(toEntity(change, version = 1L))
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
            taskRepository.save(merged)
            SyncController.UploadResult(change.id, status = "ok", serverVersion = existing.version + 1)
        }
    }

    override fun toPayload(entity: Any): Map<String, Any?> = toPayload(entity as Task)

    fun toPayload(t: Task): Map<String, Any?> = mapOf(
        "id" to t.id,
        "phaseId" to t.phaseId,
        "title" to t.title,
        "description" to t.description,
        "sortOrder" to t.sortOrder,
        "estimatedHours" to t.estimatedHours,
        "actualHours" to t.actualHours,
        "status" to t.status,
        "dueDate" to t.dueDate?.toString(),
        "version" to t.version,
        "createdAt" to t.createdAt?.toString(),
        "updatedAt" to t.updatedAt?.toString(),
    )

    private fun phaseBelongsToUser(phaseId: String, userId: String): Boolean {
        val phase = phaseRepository.findById(phaseId).orElse(null) ?: return false
        return projectRepository.existsByIdAndUserId(phase.projectId, userId)
    }

    private fun toEntity(change: SyncController.Change, version: Long): Task = Task(
        id = change.id,
        phaseId = (change.payload["phaseId"] as? String) ?: "",
        title = (change.payload["title"] as? String) ?: "",
        description = change.payload["description"] as? String,
        sortOrder = (change.payload["sortOrder"] as? Number)?.toInt() ?: 0,
        estimatedHours = (change.payload["estimatedHours"] as? Number)?.toInt() ?: 1,
        actualHours = (change.payload["actualHours"] as? Number)?.toInt() ?: 0,
        status = (change.payload["status"] as? String) ?: "pending",
        dueDate = (change.payload["dueDate"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        version = version,
    )
}
