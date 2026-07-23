package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.TaskUpload
import org.springframework.data.jpa.repository.JpaRepository

interface TaskUploadRepository : JpaRepository<TaskUpload, String> {
    fun findByTaskIdAndUserIdOrderByCreatedAtDesc(taskId: String, userId: String): List<TaskUpload>
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<TaskUpload>
    fun findByIdAndUserId(id: String, userId: String): TaskUpload?
}
