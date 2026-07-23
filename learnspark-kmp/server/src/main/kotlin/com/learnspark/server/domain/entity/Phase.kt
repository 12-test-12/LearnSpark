package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * 阶段实体（按文档 §11.2.3：phases 表）。
 *
 * 阶段 3.1：迁移目标。
 */
@Entity
@Table(name = "phases")
class Phase(
    @Id
    var id: String = "",

    @Column(name = "project_id", nullable = false)
    var projectId: String = "",

    @Column(nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "start_date")
    var startDate: LocalDate? = null,

    @Column(name = "end_date")
    var endDate: LocalDate? = null,

    @Column(nullable = false, length = 20)
    var status: String = "pending",

    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
)
