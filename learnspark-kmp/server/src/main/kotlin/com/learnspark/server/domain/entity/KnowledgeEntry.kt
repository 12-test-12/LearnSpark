package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 知识条目实体（按文档 §1.1：knowledge_entries 表）。
 *
 * 阶段二 2.4：知识库模块的核心表。
 */
@Entity
@Table(name = "knowledge_entries")
class KnowledgeEntry(
    @Id
    var id: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "folder_id")
    var folderId: String? = null,

    @Column(nullable = false)
    var title: String = "",

    @Column(columnDefinition = "MEDIUMTEXT")
    var content: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    var sourceType: SourceType = SourceType.MANUAL,

    @Column(name = "source_path")
    var sourcePath: String? = null,

    @Column(name = "file_size")
    var fileSize: Long? = null,

    @Column(name = "file_type", length = 50)
    var fileType: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false)
    var parseStatus: ParseStatus = ParseStatus.READY,

    @Column(name = "parse_error", columnDefinition = "TEXT")
    var parseError: String? = null,

    @Column(length = 500)
    var tags: String? = null,

    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
) {
    enum class SourceType { MANUAL, FILE, LINK }
    enum class ParseStatus { READY, PENDING, PROCESSING, FAILED }
}
