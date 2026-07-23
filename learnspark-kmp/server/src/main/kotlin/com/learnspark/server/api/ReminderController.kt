package com.learnspark.server.api

import com.learnspark.server.domain.entity.RepeatPattern
import com.learnspark.server.domain.entity.ReminderLog
import com.learnspark.server.domain.entity.ReminderSetting
import com.learnspark.server.domain.entity.ReminderType
import com.learnspark.server.service.ReminderService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalTime

/**
 * R3b：提醒规则 + 通知日志 API。
 *
 * Settings：
 * - GET    /api/v1/reminders/settings
 * - POST   /api/v1/reminders/settings
 * - PATCH  /api/v1/reminders/settings/{id}
 * - DELETE /api/v1/reminders/settings/{id}
 *
 * Logs（客户端轮询）：
 * - GET    /api/v1/reminders/logs?pending=true
 * - POST   /api/v1/reminders/logs/{id}/ack
 * - POST   /api/v1/reminders/logs/ack-all
 */
@RestController
@RequestMapping("/api/v1/reminders")
class ReminderController(
    private val service: ReminderService,
) {

    // === Settings ===

    @GetMapping("/settings")
    fun listSettings(@RequestHeader("X-User-Id") userId: String): Map<String, Any?> =
        mapOf("items" to service.listSettings(userId).map(::toDto))

    @GetMapping("/settings/{id}")
    fun getSetting(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> {
        val s = service.getSetting(id, userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toDto(s))
    }

    @PostMapping("/settings")
    fun create(
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: CreateRequest,
    ): ResponseEntity<Any> {
        val s = service.createSetting(
            userId = userId,
            type = req.type ?: ReminderType.CUSTOM,
            title = req.title.orEmpty(),
            message = req.message,
            targetId = req.targetId,
            triggerTime = parseTime(req.triggerTime) ?: LocalTime.of(9, 0),
            repeatPattern = req.repeatPattern ?: RepeatPattern.DAILY,
            weekdayMask = req.weekdayMask ?: 127,
            enabled = req.enabled ?: true,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(s))
    }

    @PatchMapping("/settings/{id}")
    fun update(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody req: UpdateRequest,
    ): ResponseEntity<Any> = when (
        val r = service.updateSetting(
            id = id,
            userId = userId,
            type = req.type,
            title = req.title,
            message = req.message,
            targetId = req.targetId,
            triggerTime = req.triggerTime?.let { parseTime(it) },
            repeatPattern = req.repeatPattern,
            weekdayMask = req.weekdayMask,
            enabled = req.enabled,
        )
    ) {
        is ReminderService.UpdateResult.Ok -> ResponseEntity.ok(toDto(r.setting))
        ReminderService.UpdateResult.NotFound -> ResponseEntity.notFound().build()
    }

    @DeleteMapping("/settings/{id}")
    fun delete(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> = if (service.deleteSetting(id, userId)) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.notFound().build()
    }

    // === Logs ===

    @GetMapping("/logs")
    fun listLogs(
        @RequestHeader("X-User-Id") userId: String,
        @RequestParam(required = false, defaultValue = "false") pending: Boolean,
    ): Map<String, Any?> {
        val items = if (pending) service.listPendingLogs(userId) else service.listAllLogs(userId)
        return mapOf("items" to items.map(::toLogDto))
    }

    @PostMapping("/logs/{id}/ack")
    fun ackLog(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
    ): ResponseEntity<Any> = if (service.acknowledgeLog(id, userId)) {
        ResponseEntity.noContent().build()
    } else {
        ResponseEntity.notFound().build()
    }

    @PostMapping("/logs/ack-all")
    fun ackAll(@RequestHeader("X-User-Id") userId: String): Map<String, Any> =
        mapOf("deleted" to service.acknowledgeAll(userId))

    // === helpers ===

    private fun parseTime(s: String?): LocalTime? = s?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun toDto(s: ReminderSetting) = mapOf(
        "id" to s.id,
        "userId" to s.userId,
        "type" to s.type.name,
        "title" to s.title,
        "message" to s.message,
        "targetId" to s.targetId,
        "triggerTime" to s.triggerTime.toString(),
        "repeatPattern" to s.repeatPattern.name,
        "weekdayMask" to s.weekdayMask,
        "nextFireAt" to s.nextFireAt?.toString(),
        "enabled" to s.enabled,
        "version" to s.version,
        "createdAt" to s.createdAt?.toString(),
        "updatedAt" to s.updatedAt?.toString(),
    )

    private fun toLogDto(l: ReminderLog) = mapOf(
        "id" to l.id,
        "userId" to l.userId,
        "settingId" to l.settingId,
        "firedAt" to l.firedAt.toString(),
        "title" to l.title,
        "message" to l.message,
        "targetId" to l.targetId,
        "acknowledged" to l.acknowledged,
    )

    // === DTOs ===

    data class CreateRequest(
        val type: ReminderType? = null,
        val title: String? = null,
        val message: String? = null,
        val targetId: String? = null,
        val triggerTime: String? = null,        // "HH:mm:ss" / "HH:mm"
        val repeatPattern: RepeatPattern? = null,
        val weekdayMask: Int? = null,
        val enabled: Boolean? = null,
    )

    data class UpdateRequest(
        val type: ReminderType? = null,
        val title: String? = null,
        val message: String? = null,
        val targetId: String? = null,
        val triggerTime: String? = null,
        val repeatPattern: RepeatPattern? = null,
        val weekdayMask: Int? = null,
        val enabled: Boolean? = null,
    )
}
