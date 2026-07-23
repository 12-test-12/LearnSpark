package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.KnowledgeFolder
import org.springframework.data.jpa.repository.JpaRepository

interface KnowledgeFolderRepository : JpaRepository<KnowledgeFolder, String> {
    fun findByUserId(userId: String): List<KnowledgeFolder>
    fun findByUserIdAndParentId(userId: String, parentId: String?): List<KnowledgeFolder>
    fun findByIdAndUserId(id: String, userId: String): KnowledgeFolder?
    fun deleteByUserId(userId: String): Long
}
