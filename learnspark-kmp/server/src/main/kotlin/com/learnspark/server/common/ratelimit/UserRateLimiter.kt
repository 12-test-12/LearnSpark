package com.learnspark.server.common.ratelimit

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * R2：进程内按用户限流（滑动窗口简化版：每自然分钟重置计数）。
 *
 * 为什么进程内而非 Redis：
 * - 当前服务端为单实例 docker-compose 部署
 * - Redis 引入额外基础设施依赖
 * - 后续分布式部署时，本类可换为 [Bucket4j] + Redis 实现，接口不变
 *
 * 限制：实例重启会清零计数（业务可接受，5 次/分钟的限额只是防止滥用）
 */
@Service
class UserRateLimiter {

    private val counters = ConcurrentHashMap<String, Counter>()

    /**
     * 检查用户是否在当前分钟内已用完配额。
     *
     * @return true = 允许调用（计数 +1），false = 拒绝（应返回 429）
     */
    fun tryAcquire(userId: String, permits: Int = 1, perMinute: Int = 5): Boolean {
        val now = Instant.now()
        val currentMinute = now.epochSecond / 60
        val counter = counters.compute(userId) { _, existing ->
            if (existing == null || existing.minute != currentMinute) {
                Counter(currentMinute, AtomicInteger())
            } else {
                existing
            }
        }!!
        return counter.count.addAndGet(permits) <= perMinute
    }

    /**
     * 周期清理过期计数器（避免长时间运行内存膨胀）。
     * 每 10 分钟跑一次：删除 1 分钟前已过期的计数器
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    fun cleanup() {
        val currentMinute = Instant.now().epochSecond / 60
        counters.entries.removeIf { it.value.minute < currentMinute - 1 }
    }

    private data class Counter(val minute: Long, val count: AtomicInteger)
}

