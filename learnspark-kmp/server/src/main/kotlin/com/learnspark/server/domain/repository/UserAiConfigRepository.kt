package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.UserAiConfig
import org.springframework.data.jpa.repository.JpaRepository

/**
 * R2：UserAiConfig Repository。
 */
interface UserAiConfigRepository : JpaRepository<UserAiConfig, String> {
    fun findByUserId(userId: String): List<UserAiConfig>
    fun findByUserIdAndProvider(userId: String, provider: String): UserAiConfig?
    fun deleteByUserIdAndProvider(userId: String, provider: String): Long
}
