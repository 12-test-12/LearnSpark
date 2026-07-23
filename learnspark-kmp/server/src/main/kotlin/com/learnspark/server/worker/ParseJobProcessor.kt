package com.learnspark.server.worker

import com.learnspark.server.domain.entity.FileParseJob
import com.learnspark.server.domain.repository.FileParseJobRepository
import com.learnspark.server.service.FileParseService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * 阶段 2.1 + 2.2 + 2.3：app-worker 核心调度器。
 *
 * 设计要点（按文档 §5.4）：
 * - @Scheduled(fixedDelay = 5000)  5 秒轮询一次
 * - 每次最多处理 batchSize 个任务（默认 3），避免单次占用过久
 * - SELECT ... FOR UPDATE 悲观锁：防止多 worker 实例抢同一任务
 *   （见 [FileParseJobRepository.findPendingForUpdate]）
 * - PENDING → PROCESSING → READY/FAILED 状态机
 * - worker 崩溃后重启：积压的 PENDING 任务被新 worker 抢占，自动继续
 *
 * profile 隔离：仅当 spring.profiles.active=worker 时激活。
 */
@Component
@Profile("worker")
@EnableConfigurationProperties(ParserProperties::class)
class ParseJobProcessor(
    private val jobRepository: FileParseJobRepository,
    private val fileParseService: FileParseService,
    private val props: ParserProperties,
) {
    private val log = LoggerFactory.getLogger(ParseJobProcessor::class.java)
    private val workerId = "worker-${ManagementFactory.getRuntimeMXBean().name}-${INSTANCE_ID.incrementAndGet()}"
    private val processedTotal = AtomicLong(0)
    private val failedTotal = AtomicLong(0)

    /**
     * 5 秒轮询一次。
     * - fixedDelay：上一次执行结束到下一次开始的间隔
     * - 不用 cron：避免漂移累积
     */
    @Scheduled(fixedDelayString = "\${learnspark.parser.poll-interval-ms:5000}", initialDelay = 5000)
    @Transactional
    fun pollAndProcess() {
        if (!props.enabled) {
            log.debug("[worker] disabled, skip")
            return
        }
        try {
            val pending = jobRepository.findPendingForUpdate(PageRequest.of(0, props.batchSize))
            if (pending.isEmpty()) {
                log.debug("[worker] no pending jobs (workerId={})", workerId)
                return
            }

            log.info("[worker] picked {} jobs (workerId={})", pending.size, workerId)
            pending.forEach { job ->
                try {
                    val ok = fileParseService.runJob(job, workerId)
                    if (ok) processedTotal.incrementAndGet() else failedTotal.incrementAndGet()
                } catch (e: Exception) {
                    log.error("[worker] runJob exception: job={}", job.id, e)
                    failedTotal.incrementAndGet()
                    // 防止单条任务卡死 worker：标记为 PENDING 等待下一轮（重试在 runJob 内部）
                }
            }
        } catch (e: Exception) {
            log.error("[worker] poll failed", e)
            // DB 暂时不可用：下一轮重试
        }
    }

    fun stats(): String = "workerId=$workerId processed=${processedTotal.get()} failed=${failedTotal.get()}"

    companion object {
        private val INSTANCE_ID = AtomicLong(0)
    }
}
