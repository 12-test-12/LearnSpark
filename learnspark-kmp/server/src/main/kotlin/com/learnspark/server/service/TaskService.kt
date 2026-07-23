package com.learnspark.server.service

import com.learnspark.server.domain.entity.Phase
import com.learnspark.server.domain.entity.Task
import com.learnspark.server.domain.repository.PhaseRepository
import com.learnspark.server.domain.repository.TaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * R1：Task 业务服务。
 *
 * - 必须验证 phase 归属当前 user（经由 PhaseService）
 * - status 变更后异步触发 [PhaseStatusRecalculator] 同步父级 phase 状态
 *
 * 为什么 status 是 String 而非 enum：
 * 数据库 schema 已有大量字符串状态（pending/in_progress/done/skipped），
 * 用 enum 会与 DB CHECK 约束产生额外同步成本；保持字符串降低维护复杂度。
 */
@Service
class TaskService(
    private val taskRepository: TaskRepository,
    private val phaseRepository: PhaseRepository,
    private val phaseStatusRecalculator: PhaseStatusRecalculator,
    private val phaseService: PhaseService,
) {

    fun listByPhase(phaseId: String, userId: String): List<Task> {
        // 跨级校验：phase 必须归属当前 user
        if (phaseService.get(phaseId, userId) == null) return emptyList()
        return taskRepository.findByPhaseId(phaseId)
            .sortedWith(compareBy({ it.sortOrder }, { it.title }))
    }

    fun get(id: String, userId: String): Task? {
        val task = taskRepository.findById(id).orElse(null) ?: return null
        // 通过 phase 反查 userId（避免依赖一个跨级 join repo）
        return if (phaseService.get(task.phaseId, userId) != null) task else null
    }

    @Transactional
    fun create(
        phaseId: String,
        userId: String,
        title: String,
        description: String? = null,
        sortOrder: Int = 0,
        estimatedHours: Int = 1,
        dueDate: java.time.LocalDate? = null,
    ): CreateResult {
        if (phaseService.get(phaseId, userId) == null) return CreateResult.PhaseNotFound
        val task = Task(
            id = UUID.randomUUID().toString(),
            phaseId = phaseId,
            title = title.trim(),
            description = description?.trim(),
            sortOrder = sortOrder,
            estimatedHours = estimatedHours.coerceIn(1, 1000),
            actualHours = 0,
            status = "pending",
            dueDate = dueDate,
        )
        val saved = taskRepository.save(task)
        // 新增 task 后立即重算 phase 状态（新 task 至少让 phase 进入 in_progress）
        phaseStatusRecalculator.recalculate(phaseId)
        return CreateResult.Ok(saved)
    }

    @Transactional
    fun update(
        id: String,
        userId: String,
        title: String? = null,
        description: String? = null,
        sortOrder: Int? = null,
        estimatedHours: Int? = null,
        actualHours: Int? = null,
        status: String? = null,
        dueDate: java.time.LocalDate? = null,
        baseVersion: Long? = null,
    ): UpdateResult {
        val existing = get(id, userId) ?: return UpdateResult.NotFound
        if (baseVersion != null && existing.version != baseVersion) {
            return UpdateResult.VersionConflict(existing.version, existing)
        }
        title?.let { existing.title = it.trim() }
        description?.let { existing.description = it.trim() }
        sortOrder?.let { existing.sortOrder = it }
        estimatedHours?.let { existing.estimatedHours = it.coerceIn(1, 1000) }
        actualHours?.let { existing.actualHours = it.coerceAtLeast(0) }
        status?.let { existing.status = validateStatus(it) }
        dueDate?.let { existing.dueDate = it }
        existing.version = existing.version + 1
        val saved = taskRepository.save(existing)
        // status 变化触发父 phase 状态重算
        if (status != null) phaseStatusRecalculator.recalculate(existing.phaseId)
        return UpdateResult.Ok(saved)
    }

    @Transactional
    fun delete(id: String, userId: String): Boolean {
        val existing = get(id, userId) ?: return false
        val phaseId = existing.phaseId
        taskRepository.delete(existing)
        phaseStatusRecalculator.recalculate(phaseId)
        return true
    }

    /**
     * status 字符串白名单校验。
     * 防御性：避免 Controller 直接信任客户端传入任意字符串
     */
    private fun validateStatus(s: String): String = when (s) {
        "pending", "in_progress", "done", "skipped" -> s
        else -> throw IllegalArgumentException("invalid task status: $s (expected pending|in_progress|done|skipped)")
    }

    sealed class CreateResult {
        data class Ok(val task: Task) : CreateResult()
        data object PhaseNotFound : CreateResult()
    }

    sealed class UpdateResult {
        data class Ok(val task: Task) : UpdateResult()
        data object NotFound : UpdateResult()
        data class VersionConflict(val currentVersion: Long, val current: Task) : UpdateResult()
    }
}
