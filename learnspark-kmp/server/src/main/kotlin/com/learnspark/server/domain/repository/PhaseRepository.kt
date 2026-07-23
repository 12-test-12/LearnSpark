package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.Phase
import org.springframework.data.jpa.repository.JpaRepository

interface PhaseRepository : JpaRepository<Phase, String> {
    fun findByProjectId(projectId: String): List<Phase>
    fun existsByIdAndProjectId(id: String, projectId: String): Boolean
}
