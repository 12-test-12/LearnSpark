package com.learnspark.server.service

import com.learnspark.server.domain.entity.FileParseJob
import com.learnspark.server.domain.entity.KnowledgeEntry
import com.learnspark.server.domain.repository.FileParseJobRepository
import com.learnspark.server.domain.repository.KnowledgeEntryRepository
import com.learnspark.server.service.parsers.ParserRouter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 阶段 2.2 + 2.3：文件解析服务。
 *
 * 提供两个入口：
 * - submit(): 同步 / 异步文件入口
 *   - < 1MB（learnspark.storage.sync-threshold-bytes）：同步解析，立即返回
 *   - >= 1MB：写 PENDING 任务，app-worker 异步处理
 * - runJob(): app-worker 轮询时调用，处理单条任务
 *
 * 阶段 2.3 OCR 失败重试：每次重试 retry_count+1，达到 max-retry 标记 FAILED。
 */
@Service
class FileParseService(
    private val parserRouter: ParserRouter,
    private val jobRepository: FileParseJobRepository,
    private val knowledgeRepository: KnowledgeEntryRepository,

    @Value("\${learnspark.parser.max-retry:3}")
    private val maxRetry: Int,
) {
    private val log = LoggerFactory.getLogger(FileParseService::class.java)

    /**
     * 提交文件解析任务。
     *
     * @return 解析后的文本（同步路径）或 PENDING 任务 ID（异步路径）
     */
    @Transactional
    fun submit(entryId: String, filePath: String, fileType: String, fileSize: Long): SubmitResult {
        require(parserRouter.supports(fileType)) {
            "Unsupported file type: $fileType (supported: ${parserRouter.supportedExtensions()})"
        }

        val syncThreshold = 1024L * 1024  // 1MB
        return if (fileSize < syncThreshold) {
            // 同步解析（小文件）
            log.info("[submit] sync parse: entry={}, type={}, size={}", entryId, fileType, fileSize)
            val parser = parserRouter.route(fileType)
            val result = parser.parse(filePath)
            knowledgeRepository.markReady(entryId, result.text, Instant.now())
            SubmitResult.SyncReady(text = result.text, pages = result.pages)
        } else {
            // 异步解析（大文件）→ 写 PENDING 任务
            log.info("[submit] async parse: entry={}, type={}, size={}", entryId, fileType, fileSize)
            val job = FileParseJob(
                id = UUID.randomUUID().toString(),
                entryId = entryId,
                filePath = filePath,
                fileType = fileType,
                fileSize = fileSize,
                status = FileParseJob.ParseStatus.PENDING,
            )
            jobRepository.save(job)
            // 更新知识库状态为 PENDING
            val entry = knowledgeRepository.findById(entryId).orElse(null)
            if (entry != null) {
                entry.parseStatus = KnowledgeEntry.ParseStatus.PENDING
                entry.parseError = null
                knowledgeRepository.save(entry)
            }
            SubmitResult.AsyncPending(jobId = job.id)
        }
    }

    /**
     * app-worker 轮询时调用：处理单条已抢占的任务。
     *
     * @return true=成功；false=失败但未超过重试次数；throw=致命错误
     */
    @Transactional
    fun runJob(job: FileParseJob, workerId: String): Boolean {
        log.info("[runJob] start: job={}, entry={}, type={}, retry={}",
            job.id, job.entryId, job.fileType, job.retryCount)

        // 1. 标记 PROCESSING
        job.status = FileParseJob.ParseStatus.PROCESSING
        job.workerId = workerId
        job.claimedAt = Instant.now()
        jobRepository.save(job)
        knowledgeRepository.markProcessing(job.entryId)

        return try {
            // 2. 解析
            val parser = parserRouter.route(job.fileType)
            val result = parser.parse(job.filePath)

            // 3. 写回知识库
            knowledgeRepository.markReady(job.entryId, result.text, Instant.now())

            // 4. 任务标记 READY
            job.status = FileParseJob.ParseStatus.READY
            job.completedAt = Instant.now()
            jobRepository.save(job)

            log.info("[runJob] done: job={}, pages={}, text.len={}",
                job.id, result.pages, result.text.length)
            true
        } catch (e: Exception) {
            log.error("[runJob] failed: job={}, retry={}/{}", job.id, job.retryCount, maxRetry, e)
            job.retryCount += 1
            job.errorMessage = (e.message ?: e.javaClass.simpleName).take(2000)

            if (job.retryCount >= maxRetry) {
                // 重试耗尽 → FAILED
                job.status = FileParseJob.ParseStatus.FAILED
                job.completedAt = Instant.now()
                knowledgeRepository.markFailed(job.entryId, job.errorMessage ?: "Unknown error")
                log.warn("[runJob] giving up: job={}, status=FAILED", job.id)
            } else {
                // 还有重试机会 → 重新置 PENDING，等待下一轮
                job.status = FileParseJob.ParseStatus.PENDING
                job.workerId = null
                job.claimedAt = null
            }
            jobRepository.save(job)
            false
        }
    }

    sealed class SubmitResult {
        data class SyncReady(val text: String, val pages: Int?) : SubmitResult()
        data class AsyncPending(val jobId: String) : SubmitResult()
    }
}
