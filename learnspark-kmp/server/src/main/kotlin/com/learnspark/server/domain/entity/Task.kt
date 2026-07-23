package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * 任务实体（按文档 §11.2.3：tasks 表）。
 *
 * 阶段 3.1：迁移目标。
 */
@Entity
@Table(name = "tasks")
class Task(
    @Id
    var id: String = "",

    @Column(name = "phase_id", nullable = false)
    var phaseId: String = "",

    @Column(nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "estimated_hours", nullable = false)
    var estimatedHours: Int = 1,

    @Column(name = "actual_hours", nullable = false)
    var actualHours: Int = 0,

    @Column(nullable = false, length = 20)
    var status: String = "pending",

    @Column(name = "due_date")
    var dueDate: LocalDate? = null,

    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
)
