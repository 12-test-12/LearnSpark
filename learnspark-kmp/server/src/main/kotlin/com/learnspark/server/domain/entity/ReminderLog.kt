package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * R3b：单次提醒触发记录。
 *
 * 用户在客户端轮询 GET /api/v1/notifications/pending 时拉到未确认的 log。
 * acknowledged = true 后不再出现在 pending 列表。
 */
@Entity
@Table(name = "reminder_logs")
class ReminderLog(
    @Id
    var id: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "setting_id", nullable = false)
    var settingId: String = "",

    @Column(name = "fired_at", nullable = false)
    var firedAt: Instant = Instant.now(),

    @Column(nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT")
    var message: String? = null,

    @Column(name = "target_id")
    var targetId: String? = null,

    @Column(nullable = false)
    var acknowledged: Boolean = false,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,
)
