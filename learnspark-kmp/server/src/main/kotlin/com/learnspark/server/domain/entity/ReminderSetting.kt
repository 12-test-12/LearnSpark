package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalTime

/**
 * R3b：用户自定义提醒规则。
 *
 * triggerTime 是 LocalTime（HH:mm:ss），每自然日同一时刻触发。
 * repeatPattern 控制触发频率：
 *  - once: 仅触发一次（next_fire_at 触发后置 null）
 *  - daily: 每日
 *  - weekdays: 周一到周五（忽略 weekdayMask）
 *  - weekly:  按 weekdayMask 选定的星期几触发
 *
 * weekdayMask 是位掩码：bit0=Mon, bit1=Tue, ..., bit6=Sun
 * 默认 127 = 全选
 */
@Entity
@Table(name = "reminder_settings")
class ReminderSetting(
    @Id
    var id: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: ReminderType = ReminderType.CUSTOM,

    @Column(nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT")
    var message: String? = null,

    @Column(name = "target_id")
    var targetId: String? = null,

    @Column(name = "trigger_time", nullable = false)
    var triggerTime: LocalTime = LocalTime.of(9, 0),

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_pattern", nullable = false)
    var repeatPattern: RepeatPattern = RepeatPattern.DAILY,

    @Column(name = "weekday_mask", nullable = false)
    var weekdayMask: Int = 127,

    @Column(name = "next_fire_at")
    var nextFireAt: Instant? = null,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
)

enum class ReminderType { TASK_DUE, CHECK_IN, CUSTOM }
enum class RepeatPattern { ONCE, DAILY, WEEKDAYS, WEEKLY }
