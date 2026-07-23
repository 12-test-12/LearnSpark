package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * R3a：知识库文件夹（多层树）。
 *
 * path 冗余：完整路径 "/编程/Java/多线程"，避免递归查询
 * depth 冗余：根=0，子=1... 上限 MAX_DEPTH（10）
 *
 * 移动/重命名时同步刷新 path + descendants.path
 */
@Entity
@Table(name = "knowledge_folders")
class KnowledgeFolder(
    @Id
    var id: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "parent_id")
    var parentId: String? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column
    var color: String? = null,

    @Column
    var icon: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(nullable = false)
    var path: String = "/",

    @Column(nullable = false)
    var depth: Int = 0,

    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
) {
    companion object {
        const val MAX_DEPTH = 10
    }
}
