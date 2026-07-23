package com.learnspark.server.service.sync

import com.learnspark.server.api.SyncController
import com.learnspark.server.domain.entity.Submission
import com.learnspark.server.domain.repository.SubmissionRepository
import com.learnspark.server.domain.repository.TaskRepository
import org.springframework.stereotype.Component

/**
 * R2：Submission 表同步 handler。
 *
 * 跨级 ownership 校验：
 * - submission 必须挂在 userId 拥有的 project 下的 task 内
 * - 经由 task → phase → project 反查，避免重复权限逻辑
 */
@Component
class SubmissionSyncHandler(
    private val submissionRepository: SubmissionRepository,
    private val taskRepository: TaskRepository,
    private val phaseRepository: com.learnspark.server.domain.repository.PhaseRepository,
    private val projectRepository: com.learnspark.server.domain.repository.ProjectRepository,
) : SyncTableHandler {
    override val tableName: String = "submissions"

    override fun apply(userId: String, change: SyncController.Change): SyncController.UploadResult {
        val taskId = change.payload["taskId"] as? String
        if (taskId == null) {
            return SyncController.UploadResult(change.id, status = "bad_request", serverVersion = 0L)
        }
        if (!taskBelongsToUser(taskId, userId)) {
            return SyncController.UploadResult(change.id, status = "forbidden", serverVersion = 0L)
        }
        val existing = submissionRepository.findById(change.id).orElse(null)
        return if (existing == null) {
            if (change.baseVersion != 0L) {
                return SyncController.UploadResult(change.id, status = "conflict", serverVersion = 0L)
            }
            submissionRepository.save(toEntity(change, userId, version = 1L))
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
            // 注意：sync 仅同步 content 与基础字段，ai_* 字段由服务端评审后写回
            submissionRepository.save(merged)
            SyncController.UploadResult(change.id, status = "ok", serverVersion = existing.version + 1)
        }
    }

    override fun toPayload(entity: Any): Map<String, Any?> = toPayload(entity as Submission)

    fun toPayload(s: Submission): Map<String, Any?> = mapOf(
        "id" to s.id,
        "taskId" to s.taskId,
        "userId" to s.userId,
        "content" to s.content,
        "aiScore" to s.aiScore,
        "aiFeedback" to s.aiFeedback,
        "aiHighlights" to s.aiHighlights,
        "reviewedAt" to s.reviewedAt?.toString(),
        "version" to s.version,
        "createdAt" to s.createdAt?.toString(),
        "updatedAt" to s.updatedAt?.toString(),
    )

    private fun taskBelongsToUser(taskId: String, userId: String): Boolean {
        val task = taskRepository.findById(taskId).orElse(null) ?: return false
        val phase = phaseRepository.findById(task.phaseId).orElse(null) ?: return false
        return projectRepository.existsByIdAndUserId(phase.projectId, userId)
    }

    private fun toEntity(change: SyncController.Change, userId: String, version: Long): Submission = Submission(
        id = change.id,
        taskId = (change.payload["taskId"] as? String) ?: "",
        userId = userId,
        content = (change.payload["content"] as? String) ?: "",
        version = version,
    )
}
