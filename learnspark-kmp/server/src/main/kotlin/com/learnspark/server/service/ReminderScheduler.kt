package com.learnspark.server.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * R3b：每分钟跑一次的调度器，扫描到期提醒并落库日志。
 *
 * 设计要点：
 * - 进程内单实例（app-api 跑，app-worker 也可同时跑 —— 必须保证幂等性）
 *   通过 [ReminderService.tick] 在事务内一次性读 + 写，最坏情况：双 worker 各跑一次，触发两条日志
 *   → 生产可加 SELECT ... FOR UPDATE SKIP LOCKED（MySQL 8+）保证独占；本阶段接受幂等重复
 * - 失败容错：tick 内部 try/catch 已在 Service 层做，本组件不抛
 *
 * 调度频率：fixedDelay = 60s（任务结束 60s 后再启动，避免与上一次重叠）
 */
@Component
class ReminderScheduler(
    private val reminderService: ReminderService,
) {
    private val log = LoggerFactory.getLogger(ReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60_000L, initialDelay = 15_000L)
    fun run() {
        try {
            val r = reminderService.tick()
            if (r.fired > 0) {
                log.info("ReminderScheduler tick: fired={} rescheduled={}", r.fired, r.rescheduled)
            }
        } catch (e: Exception) {
            // 永远不让 scheduler 自己挂掉下一轮
            log.warn("ReminderScheduler tick failed: {}", e.message)
        }
    }
}
