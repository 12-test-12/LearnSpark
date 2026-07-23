package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * R4c：任务上传文件元数据。
 *
 * - file_path: 实际存储路径（uploads/{userId}/{taskId}/{uuid}.{ext}）
 * - knowledge_entry_id: 解析成功后落到 knowledge_entries 的 id
 * - upload_status: pending → parsing → ready/failed
 */
@Entity
@Table(name = "task_uploads")
class TaskUpload(
    @Id
    var id: String = "",

    @Column(name = "task_id", nullable = false)
    var taskId: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "knowledge_entry_id")
    var knowledgeEntryId: String? = null,

    @Column(name = "folder_id")
    var folderId: String? = null,

    @Column(name = "file_name", nullable = false)
    var fileName: String = "",

    @Column(name = "file_path", nullable = false, length = 500)
    var filePath: String = "",

    @Column(name = "file_type", nullable = false, length = 50)
    var fileType: String = "",

    @Column(name = "file_size", nullable = false)
    var fileSize: Long = 0L,

    @Column(name = "upload_status", nullable = false, length = 20)
    var uploadStatus: String = "pending",

    @Column(name = "parse_error", columnDefinition = "TEXT")
    var parseError: String? = null,

    @Column(nullable = false)
    var version: Long = 0L,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null,
)
