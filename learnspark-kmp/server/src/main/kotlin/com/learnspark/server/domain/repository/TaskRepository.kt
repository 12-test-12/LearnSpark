package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.Task
import org.springframework.data.jpa.repository.JpaRepository

interface TaskRepository : JpaRepository<Task, String> {
    fun findByPhaseId(phaseId: String): List<Task>
}
