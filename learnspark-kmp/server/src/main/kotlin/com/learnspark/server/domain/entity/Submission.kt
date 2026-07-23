package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 任务提交实体（按文档 §11.2.3：submissions 表）。
 *
 * 字段映射：旧版 content_md → 新版 content（统一字段命名）。
 */
@Entity
@Table(name = "submissions")
class Submission(
    @Id
    var id: String = "",

    @Column(name = "task_id", nullable = false)
    var taskId: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    var content: String = "",

    @Column(name = "ai_score")
    var aiScore: Int? = null,

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    var aiFeedback: String? = null,

    @Column(name = "ai_highlights", columnDefinition = "TEXT")
    var aiHighlights: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
)
