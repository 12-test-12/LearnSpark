package com.learnspark.server.service

import com.learnspark.server.domain.entity.Phase
import com.learnspark.server.domain.repository.PhaseRepository
import com.learnspark.server.domain.repository.ProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * R1：Phase 业务服务。
 *
 * - 强归属校验：phase 必须挂在 userId 拥有的 project 下
 * - status 字段由 PhaseStatusRecalculator 重算（不在本服务中维护写入），避免双向耦合
 * - 删除 phase 走 ON DELETE CASCADE，由 DB 自动清理下属 task
 *
 * 为什么不在 PhaseService 内嵌 status 重算：
 * 1) PhaseService 与 TaskService 双向依赖会导致循环引用
 * 2) status 重算是一个独立语义，由 [PhaseStatusRecalculator] 承担
 */
@Service
class PhaseService(
    private val phaseRepository: PhaseRepository,
    private val projectRepository: ProjectRepository,
) {

    fun listByProject(projectId: String, userId: String): List<Phase> {
        if (!projectRepository.existsByIdAndUserId(projectId, userId)) return emptyList()
        // 排序：sortOrder ASC + name 兜底，保证展示稳定
        return phaseRepository.findByProjectId(projectId)
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }

    fun get(id: String, userId: String): Phase? {
        val phase = phaseRepository.findById(id).orElse(null) ?: return null
        // 跨级校验：必须挂在该 user 拥有的 project 下
        val project = projectRepository.findById(phase.projectId).orElse(null) ?: return null
        if (project.userId != userId) return null
        return phase
    }

    @Transactional
    fun create(
        projectId: String,
        userId: String,
        name: String,
        description: String? = null,
        sortOrder: Int = 0,
        startDate: java.time.LocalDate? = null,
        endDate: java.time.LocalDate? = null,
    ): CreateResult {
        if (!projectRepository.existsByIdAndUserId(projectId, userId)) return CreateResult.ProjectNotFound
        val phase = Phase(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            name = name.trim(),
            description = description?.trim(),
            sortOrder = sortOrder,
            startDate = startDate,
            endDate = endDate,
            status = "pending",
        )
        return CreateResult.Ok(phaseRepository.save(phase))
    }

    @Transactional
    fun update(
        id: String,
        userId: String,
        name: String? = null,
        description: String? = null,
        sortOrder: Int? = null,
        startDate: java.time.LocalDate? = null,
        endDate: java.time.LocalDate? = null,
        status: String? = null,
        baseVersion: Long? = null,
    ): UpdateResult {
        val existing = get(id, userId) ?: return UpdateResult.NotFound
        if (baseVersion != null && existing.version != baseVersion) {
            return UpdateResult.VersionConflict(existing.version, existing)
        }
        name?.let { existing.name = it.trim() }
        description?.let { existing.description = it.trim() }
        sortOrder?.let { existing.sortOrder = it }
        startDate?.let { existing.startDate = it }
        endDate?.let { existing.endDate = it }
        status?.let { existing.status = it }
        existing.version = existing.version + 1
        return UpdateResult.Ok(phaseRepository.save(existing))
    }

    @Transactional
    fun delete(id: String, userId: String): Boolean {
        val existing = get(id, userId) ?: return false
        phaseRepository.delete(existing)
        return true
    }

    sealed class CreateResult {
        data class Ok(val phase: Phase) : CreateResult()
        data object ProjectNotFound : CreateResult()
    }

    sealed class UpdateResult {
        data class Ok(val phase: Phase) : UpdateResult()
        data object NotFound : UpdateResult()
        data class VersionConflict(val currentVersion: Long, val current: Phase) : UpdateResult()
    }
}
