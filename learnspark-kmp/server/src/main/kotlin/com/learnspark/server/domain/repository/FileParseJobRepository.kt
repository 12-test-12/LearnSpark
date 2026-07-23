package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.FileParseJob
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface FileParseJobRepository : JpaRepository<FileParseJob, String> {

    /**
     * 阶段 2.1 关键 SQL：SELECT ... FOR UPDATE 悲观锁。
     *
     * 防止多 worker 实例同时抢到同一任务。
     * 超时时间 3 秒（javax.persistence.lock.timeout hint），
     * 避免 worker 长时间阻塞。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
        QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    )
    @Query(
        """
        SELECT j FROM FileParseJob j
        WHERE j.status = com.learnspark.server.domain.entity.FileParseJob.ParseStatus.PENDING
        ORDER BY j.createdAt ASC
        """
    )
    fun findPendingForUpdate(pageable: Pageable): List<FileParseJob>

    fun findByEntryIdOrderByCreatedAtDesc(entryId: String): List<FileParseJob>

    fun findByEntryIdAndStatus(entryId: String, status: FileParseJob.ParseStatus): Optional<FileParseJob>
}
