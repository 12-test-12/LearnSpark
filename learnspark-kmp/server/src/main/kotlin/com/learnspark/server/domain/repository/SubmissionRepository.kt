package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.Submission
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface SubmissionRepository : JpaRepository<Submission, String> {
    fun findByTaskId(taskId: String): List<Submission>
    fun findByUserId(userId: String): List<Submission>
    fun findByUserIdOrderByUpdatedAtDesc(userId: String, pageable: Pageable): Page<Submission>
}
