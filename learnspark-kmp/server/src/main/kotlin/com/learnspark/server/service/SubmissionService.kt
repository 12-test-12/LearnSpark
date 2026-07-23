package com.learnspark.server.service

import com.learnspark.server.domain.entity.Submission
import com.learnspark.server.domain.repository.SubmissionRepository
import com.learnspark.server.domain.repository.TaskRepository
import com.learnspark.server.service.ai.AiConfigService
import com.learnspark.server.service.ai.OpenAICompatibleClient
import com.learnspark.server.service.ai.SubmissionReviewer
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * R2：任务提交服务（含 AI 评审）。
 *
 * 写流程：
 * 1) create(content) → 立即返回 submission（不阻塞）
 * 2) 异步 @Async 触发 AI 评审
 * 3) DeepSeek 返回 → 写回 ai_score/ai_feedback/ai_highlights/reviewed_at
 *
 * 读流程：
 * - listByTask / get
 * - 跨级 ownership：submission → task → phase → project → user
 */
@Service
class SubmissionService(
    private val submissionRepository: SubmissionRepository,
    private val taskRepository: TaskRepository,
    private val phaseService: PhaseService,
    private val aiConfigService: AiConfigService,
    private val submissionReviewer: SubmissionReviewer,
) {
    private val log = LoggerFactory.getLogger(SubmissionService::class.java)

    fun listByTask(taskId: String, userId: String): List<Submission> {
        if (!taskBelongsToUser(taskId, userId)) return emptyList()
        return submissionRepository.findByTaskId(taskId)
            .sortedByDescending { it.updatedAt }
    }

    fun get(id: String, userId: String): Submission? {
        val s = submissionRepository.findById(id).orElse(null) ?: return null
        return if (taskBelongsToUser(s.taskId, userId)) s else null
    }

    @Transactional
    fun create(
        taskId: String,
        userId: String,
        content: String,
        triggerReview: Boolean = true,
    ): CreateResult {
        if (!taskBelongsToUser(taskId, userId)) return CreateResult.TaskNotFound
        val submission = Submission(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            userId = userId,
            content = content,
            version = 1L,
        )
        val saved = submissionRepository.save(submission)
        log.info("submission created id={} user={} task={}", saved.id, userId, taskId)
        if (triggerReview) triggerAiReviewAsync(saved.id, userId)
        return CreateResult.Ok(saved)
    }

    @Transactional
    fun update(
        id: String,
        userId: String,
        content: String? = null,
        baseVersion: Long? = null,
    ): UpdateResult {
        val existing = get(id, userId) ?: return UpdateResult.NotFound
        if (baseVersion != null && existing.version != baseVersion) {
            return UpdateResult.VersionConflict(existing.version, existing)
        }
        content?.let { existing.content = it }
        existing.version = existing.version + 1
        val saved = submissionRepository.save(existing)
        return UpdateResult.Ok(saved)
    }

    @Transactional
    fun delete(id: String, userId: String): Boolean {
        val existing = get(id, userId) ?: return false
        submissionRepository.delete(existing)
        return true
    }

    /**
     * 异步触发 AI 评审：
     * - 用户未配置 / 禁用 DeepSeek → 静默跳过（submission.reviewed_at 不更新）
     * - DeepSeek 错误 → reviewed_at = now + ai_feedback = error message
     * - 成功 → 写回 score/feedback/highlights/reviewed_at
     */
    @Async
    fun triggerAiReviewAsync(submissionId: String, userId: String) {
        try {
            submissionReviewer.review(submissionId, userId)
        } catch (e: Exception) {
            log.warn("AI review background failed submission={}: {}", submissionId, e.message)
        }
    }

    private fun taskBelongsToUser(taskId: String, userId: String): Boolean {
        val task = taskRepository.findById(taskId).orElse(null) ?: return false
        return phaseService.get(task.phaseId, userId) != null
    }

    sealed class CreateResult {
        data class Ok(val submission: Submission) : CreateResult()
        data object TaskNotFound : CreateResult()
    }

    sealed class UpdateResult {
        data class Ok(val submission: Submission) : UpdateResult()
        data object NotFound : UpdateResult()
        data class VersionConflict(val currentVersion: Long, val current: Submission) : UpdateResult()
    }
}
