package com.learnspark.server.service

import com.learnspark.server.domain.repository.PhaseRepository
import com.learnspark.server.domain.repository.TaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * R1：Phase 状态聚合器。
 *
 * 聚合规则（按优先级匹配）：
 * 1. phase 下无 task  →  pending
 * 2. 全部 task done  →  done
 * 3. 任一 task in_progress / done  →  in_progress
 * 4. 其他（含 skipped）→ pending
 *
 * 为什么独立成组件：
 * - TaskService / PhaseService 互相引用会产生循环依赖
 * - 状态聚合是一个独立语义，可单元测试
 * - 未来 Phase 还有里程碑 / 甘特图特性时，仅需扩展此组件
 */
@Service
class PhaseStatusRecalculator(
    private val phaseRepository: PhaseRepository,
    private val taskRepository: TaskRepository,
) {

    @Transactional
    fun recalculate(phaseId: String) {
        val phase = phaseRepository.findById(phaseId).orElse(null) ?: return
        val tasks = taskRepository.findByPhaseId(phaseId)
        val newStatus = aggregate(tasks.map { it.status })
        // 幂等：无变化不写
        if (phase.status == newStatus) return
        phase.status = newStatus
        phase.version = phase.version + 1
        phaseRepository.save(phase)
    }

    private fun aggregate(taskStatuses: List<String>): String = when {
        taskStatuses.isEmpty() -> "pending"
        taskStatuses.all { it == "done" } -> "done"
        taskStatuses.any { it == "in_progress" || it == "done" } -> "in_progress"
        else -> "pending"
    }
}
