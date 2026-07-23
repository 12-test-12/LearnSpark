package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.TaskArticleLink
import org.springframework.data.jpa.repository.JpaRepository

interface TaskArticleLinkRepository : JpaRepository<TaskArticleLink, String> {
    fun findByTaskIdAndUserIdOrderByRelevanceDesc(taskId: String, userId: String): List<TaskArticleLink>
    fun findByIdAndUserId(id: String, userId: String): TaskArticleLink?
    fun deleteByTaskIdAndUserIdAndEntryId(taskId: String, userId: String, entryId: String): Int
    fun findByEntryIdAndUserId(entryId: String, userId: String): List<TaskArticleLink>
}
