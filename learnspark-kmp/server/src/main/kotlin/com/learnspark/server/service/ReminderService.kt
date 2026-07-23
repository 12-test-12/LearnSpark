package com.learnspark.server.service

import com.learnspark.server.domain.entity.RepeatPattern
import com.learnspark.server.domain.entity.ReminderLog
import com.learnspark.server.domain.entity.ReminderSetting
import com.learnspark.server.domain.entity.ReminderType
import com.learnspark.server.domain.repository.ReminderLogRepository
import com.learnspark.server.domain.repository.ReminderSettingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * R3b：用户提醒服务。
 *
 * 职责：
 * - settings 的 CRUD
 * - 计算 next_fire_at（按 repeatPattern + weekdayMask）
 * - 把一次触发落地为 ReminderLog（用于客户端轮询）
 * - scheduler 每分钟调用 [tick] 完成 fire + reschedule
 */
@Service
class ReminderService(
    private val settingRepository: ReminderSettingRepository,
    private val logRepository: ReminderLogRepository,
) {
    private val log = LoggerFactory.getLogger(ReminderService::class.java)
    private val zone: ZoneId = ZoneId.systemDefault()

    // === Settings CRUD ===

    fun listSettings(userId: String): List<ReminderSetting> =
        settingRepository.findByUserId(userId).sortedBy { it.triggerTime }

    fun getSetting(id: String, userId: String): ReminderSetting? {
        val s = settingRepository.findById(id).orElse(null) ?: return null
        if (s.userId != userId) return null
        return s
    }

    @Transactional
    fun createSetting(
        userId: String,
        type: ReminderType,
        title: String,
        message: String?,
        targetId: String?,
        triggerTime: LocalTime,
        repeatPattern: RepeatPattern,
        weekdayMask: Int,
        enabled: Boolean,
    ): ReminderSetting {
        val s = ReminderSetting(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = type,
            title = title.trim().ifBlank { "未命名提醒" },
            message = message?.takeIf { it.isNotBlank() },
            targetId = targetId,
            triggerTime = triggerTime,
            repeatPattern = repeatPattern,
            weekdayMask = weekdayMask.coerceIn(0, 127),
            enabled = enabled,
            version = 1L,
        )
        s.nextFireAt = computeNextFire(s, now())
        return settingRepository.save(s)
    }

    @Transactional
    fun updateSetting(
        id: String,
        userId: String,
        type: ReminderType?,
        title: String?,
        message: String?,
        targetId: String?,
        triggerTime: LocalTime?,
        repeatPattern: RepeatPattern?,
        weekdayMask: Int?,
        enabled: Boolean?,
    ): UpdateResult {
        val s = getSetting(id, userId) ?: return UpdateResult.NotFound
        type?.let { s.type = it }
        title?.let { s.title = it.trim().ifBlank { s.title } }
        if (message != null) s.message = message.takeIf { it.isNotBlank() }
        targetId?.let { s.targetId = it }
        triggerTime?.let { s.triggerTime = it }
        repeatPattern?.let { s.repeatPattern = it }
        weekdayMask?.let { s.weekdayMask = it.coerceIn(0, 127) }
        enabled?.let { s.enabled = it }
        s.version = s.version + 1
        // 修改时间 / 重复模式时，重算 next_fire_at
        s.nextFireAt = computeNextFire(s, now())
        return UpdateResult.Ok(settingRepository.save(s))
    }

    @Transactional
    fun deleteSetting(id: String, userId: String): Boolean {
        val s = getSetting(id, userId) ?: return false
        settingRepository.delete(s)
        return true
    }

    // === Logs ===

    fun listPendingLogs(userId: String): List<ReminderLog> =
        logRepository.findByUserIdAndAcknowledgedOrderByFiredAtDesc(userId, false)

    fun listAllLogs(userId: String): List<ReminderLog> =
        logRepository.findByUserIdOrderByFiredAtDesc(userId)

    @Transactional
    fun acknowledgeLog(id: String, userId: String): Boolean {
        val l = logRepository.findById(id).orElse(null) ?: return false
        if (l.userId != userId) return false
        if (l.acknowledged) return true
        l.acknowledged = true
        logRepository.save(l)
        return true
    }

    @Transactional
    fun acknowledgeAll(userId: String): Int {
        val n = logRepository.deleteByUserIdAndAcknowledged(userId, false)
        return n.toInt()
    }

    // === Scheduler tick（每分钟由 ReminderScheduler 调用）===

    /**
     * 扫描所有 enabled + next_fire_at <= now 的 setting：
     * 1) 写一条 ReminderLog
     * 2) 重算 next_fire_at
     * 3) 一次性（ONCE）的下次置 null
     */
    @Transactional
    fun tick(now: Instant = Instant.now()): TickResult {
        val due = settingRepository.findByEnabledTrueAndNextFireAtLessThanEqualOrderByNextFireAtAsc(now)
        if (due.isEmpty()) return TickResult(0, 0)
        var firedCount = 0
        var rescheduledCount = 0
        due.forEach { s ->
            val logRow = ReminderLog(
                id = UUID.randomUUID().toString(),
                userId = s.userId,
                settingId = s.id,
                firedAt = now,
                title = s.title,
                message = s.message,
                targetId = s.targetId,
                acknowledged = false,
            )
            logRepository.save(logRow)
            firedCount += 1
            val nextFire = computeNextFire(s, now.plusSeconds(60))
            s.nextFireAt = nextFire
            s.version = s.version + 1
            settingRepository.save(s)
            rescheduledCount += 1
        }
        return TickResult(firedCount, rescheduledCount)
    }

    // === Helper ===

    /**
     * 计算下一次触发时间（绝对时刻）。
     *
     * - ONCE：已过则 null
     * - DAILY：下一个 HH:mm
     * - WEEKDAYS：周一~周五
     * - WEEKLY：按 weekdayMask 命中
     */
    fun computeNextFire(s: ReminderSetting, from: Instant): Instant? {
        if (!s.enabled && s.nextFireAt == null) return null
        val fromLocal = from.atZone(zone).toLocalDateTime()
        val candidateDate = fromLocal.toLocalDate()
        val candidateTime = s.triggerTime
        for (i in 0..7) {
            val date = candidateDate.plusDays(i.toLong())
            val dow = date.dayOfWeek
            if (!matchesDay(s.repeatPattern, s.weekdayMask, dow)) continue
            val dt = LocalDateTime.of(date, candidateTime)
            val instant = dt.atZone(zone).toInstant()
            if (instant.isAfter(from) || i == 0 && instant == from) {
                return if (s.repeatPattern == RepeatPattern.ONCE) {
                    // ONCE：保证只返回一次；用户已经触发过（nextFireAt < now）则 null
                    if (s.nextFireAt != null && s.nextFireAt!!.isBefore(from)) null else instant
                } else {
                    instant
                }
            }
        }
        return null
    }

    private fun matchesDay(pattern: RepeatPattern, mask: Int, dow: DayOfWeek): Boolean {
        return when (pattern) {
            RepeatPattern.DAILY -> true
            RepeatPattern.WEEKDAYS -> dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
            RepeatPattern.WEEKLY -> {
                val bit = when (dow) {
                    DayOfWeek.MONDAY -> 1
                    DayOfWeek.TUESDAY -> 2
                    DayOfWeek.WEDNESDAY -> 4
                    DayOfWeek.THURSDAY -> 8
                    DayOfWeek.FRIDAY -> 16
                    DayOfWeek.SATURDAY -> 32
                    DayOfWeek.SUNDAY -> 64
                }
                (mask and bit) != 0
            }
            RepeatPattern.ONCE -> {
                // ONCE：仍要 match 当天，让当天能触发；之后会被 null 掉
                val today = LocalDate.now(zone).dayOfWeek
                dow == today
            }
        }
    }

    private fun now(): Instant = Instant.now()

    data class TickResult(val fired: Int, val rescheduled: Int)

    sealed class UpdateResult {
        data class Ok(val setting: ReminderSetting) : UpdateResult()
        data object NotFound : UpdateResult()
    }
}
