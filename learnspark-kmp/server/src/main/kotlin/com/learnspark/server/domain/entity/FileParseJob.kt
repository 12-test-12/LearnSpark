package com.learnspark.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 文件解析任务（按文档 §八 V4 + §五.4）。
 *
 * 状态机: PENDING -> PROCESSING -> READY / FAILED
 *
 * app-worker 通过 @Scheduled 定时轮询 + SELECT FOR UPDATE 抢占任务。
 */
@Entity
@Table(name = "file_parse_jobs")
class FileParseJob(
    @Id
    var id: String = "",

    @Column(name = "entry_id", nullable = false)
    var entryId: String = "",

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    var filePath: String = "",

    @Column(name = "file_type", nullable = false, length = 20)
    var fileType: String = "",

    @Column(name = "file_size", nullable = false)
    var fileSize: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ParseStatus = ParseStatus.PENDING,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "worker_id", length = 100)
    var workerId: String? = null,

    @Column(name = "claimed_at")
    var claimedAt: Instant? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,
) {
    enum class ParseStatus { PENDING, PROCESSING, READY, FAILED }
}
