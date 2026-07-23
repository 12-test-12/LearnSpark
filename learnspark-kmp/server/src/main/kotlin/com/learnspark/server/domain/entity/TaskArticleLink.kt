package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * R4c：AI 标注的可参考文章（任务 ↔ 知识条目 多对多）。
 *
 * - reason: AI 解释为什么这篇文章能解决该任务
 * - relevance: 0-100 评分（用户可手动覆盖）
 * - source: "ai"（AI 建议）/ "manual"（用户手动添加）
 */
@Entity
@Table(name = "task_article_links")
class TaskArticleLink(
    @Id
    var id: String = "",

    @Column(name = "task_id", nullable = false)
    var taskId: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "entry_id", nullable = false)
    var entryId: String = "",

    @Column(nullable = false, length = 500)
    var reason: String = "",

    @Column(nullable = false)
    var relevance: Int = 50,

    @Column(nullable = false, length = 20)
    var source: String = "ai",

    @Column(nullable = false)
    var version: Long = 0L,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
)
