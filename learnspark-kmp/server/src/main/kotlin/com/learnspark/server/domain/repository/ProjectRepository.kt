package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.Project
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProjectRepository : JpaRepository<Project, String> {
    fun findByUserId(userId: String): List<Project>
    fun findByUserIdOrderByUpdatedAtDesc(userId: String, pageable: Pageable): Page<Project>
    fun existsByIdAndUserId(id: String, userId: String): Boolean
}
