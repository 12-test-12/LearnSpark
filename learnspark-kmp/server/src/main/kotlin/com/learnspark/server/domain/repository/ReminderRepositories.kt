package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.ReminderLog
import com.learnspark.server.domain.entity.ReminderSetting
import org.springframework.data.jpa.repository.JpaRepository

interface ReminderSettingRepository : JpaRepository<ReminderSetting, String> {
    fun findByUserId(userId: String): List<ReminderSetting>
    fun findByIdAndUserId(id: String, userId: String): ReminderSetting?
    fun findByEnabledTrueAndNextFireAtLessThanEqualOrderByNextFireAtAsc(
        now: java.time.Instant,
    ): List<ReminderSetting>
}

interface ReminderLogRepository : JpaRepository<ReminderLog, String> {
    fun findByUserIdAndAcknowledgedOrderByFiredAtDesc(userId: String, acknowledged: Boolean): List<ReminderLog>
    fun findByUserIdOrderByFiredAtDesc(userId: String): List<ReminderLog>
    fun deleteByUserIdAndAcknowledged(userId: String, acknowledged: Boolean): Long
}
