package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * 项目实体（按文档 §11.2.3：projects 表）。
 *
 * 阶段 3.1：旧版 Vue3 数据迁移目标。
 */
@Entity
@Table(name = "projects")
class Project(
    @Id
    var id: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(columnDefinition = "TEXT")
    var goal: String? = null,

    @Column(name = "cover_color", length = 20)
    var coverColor: String? = null,

    @Column(name = "daily_hours", nullable = false)
    var dailyHours: Int = 2,

    @Column(name = "is_ai_generated", nullable = false)
    var isAiGenerated: Boolean = false,

    @Column(nullable = false, length = 20)
    var status: String = "active",

    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
)
