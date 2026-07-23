package com.learnspark.server.service

import com.learnspark.server.domain.entity.Project
import com.learnspark.server.domain.repository.ProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * R1：Project 业务服务。
 *
 * - list/get/create/update/delete 走标准 CRUD
 * - update 走乐观锁：baseVersion 与当前不一致 → 409 VersionConflict
 * - delete 是真删（不保留历史），由 ON DELETE CASCADE 同步清理下属 phase/task/submission
 *
 * 为什么不用软删除：项目属于用户核心资产，用户主动删除意图明确；
 * 误删通过 backup-mysql.sh 兜底（30 天本地保留）。
 */
@Service
class ProjectService(
    private val repository: ProjectRepository,
) {

    fun list(userId: String): List<Project> = repository.findByUserId(userId)

    fun get(id: String, userId: String): Project? {
        val p = repository.findById(id).orElse(null) ?: return null
        // 跨用户访问统一返回 null，避免存在性泄漏
        if (p.userId != userId) return null
        return p
    }

    @Transactional
    fun create(
        userId: String,
        name: String,
        description: String? = null,
        goal: String? = null,
        coverColor: String? = null,
        dailyHours: Int = 2,
        isAiGenerated: Boolean = false,
    ): Project {
        // 防御性：name 必填已在 Controller 校验；这里再次 trim 防止首尾空白
        val project = Project(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = name.trim(),
            description = description?.trim(),
            goal = goal?.trim(),
            coverColor = coverColor,
            dailyHours = dailyHours.coerceIn(1, 24),       // 上限 24h 避免异常输入
            isAiGenerated = isAiGenerated,
            status = "active",
        )
        return repository.save(project)
    }

    @Transactional
    fun update(
        id: String,
        userId: String,
        name: String? = null,
        description: String? = null,
        goal: String? = null,
        coverColor: String? = null,
        dailyHours: Int? = null,
        status: String? = null,
        baseVersion: Long? = null,
    ): UpdateResult {
        val existing = get(id, userId) ?: return UpdateResult.NotFound
        if (baseVersion != null && existing.version != baseVersion) {
            return UpdateResult.VersionConflict(existing.version, existing)
        }
        // null 字段不覆盖，保持 PATCH 语义
        name?.let { existing.name = it.trim() }
        description?.let { existing.description = it.trim() }
        goal?.let { existing.goal = it.trim() }
        coverColor?.let { existing.coverColor = it }
        dailyHours?.let { existing.dailyHours = it.coerceIn(1, 24) }
        status?.let { existing.status = it }
        existing.version = existing.version + 1
        return UpdateResult.Ok(repository.save(existing))
    }

    @Transactional
    fun delete(id: String, userId: String): Boolean {
        val existing = get(id, userId) ?: return false
        repository.delete(existing)
        return true
    }

    /**
     * 阶段 3.1：旧版数据迁移专用 create。
     * 允许指定 id（保留旧版 ID），与 KnowledgeService.createLegacy 思路一致。
     */
    @Transactional
    fun createLegacy(
        id: String,
        userId: String,
        name: String,
        description: String? = null,
        goal: String? = null,
        coverColor: String? = null,
        dailyHours: Int = 2,
        isAiGenerated: Boolean = false,
        status: String = "active",
        version: Long = 1L,
    ): Project {
        val project = Project(
            id = id,
            userId = userId,
            name = name,
            description = description,
            goal = goal,
            coverColor = coverColor,
            dailyHours = dailyHours,
            isAiGenerated = isAiGenerated,
            status = status,
            version = version,
        )
        return repository.save(project)
    }

    sealed class UpdateResult {
        data class Ok(val project: Project) : UpdateResult()
        data object NotFound : UpdateResult()
        data class VersionConflict(val currentVersion: Long, val current: Project) : UpdateResult()
    }
}
