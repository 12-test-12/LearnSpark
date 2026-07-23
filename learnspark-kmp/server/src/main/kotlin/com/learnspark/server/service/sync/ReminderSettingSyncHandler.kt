package com.learnspark.server.service.sync

import com.learnspark.server.api.SyncController
import com.learnspark.server.domain.entity.ReminderSetting
import com.learnspark.server.domain.repository.ReminderSettingRepository
import org.springframework.stereotype.Component

/**
 * R3b：ReminderSetting 表同步 handler。
 *
 * - 客户端在桌面端改了时间 / 启停后，把最新 version 推上来
 * - 不会回传 nextFireAt（服务端独占计算字段）
 */
@Component
class ReminderSettingSyncHandler(
    private val repository: ReminderSettingRepository,
) : SyncTableHandler {
    override val tableName: String = "reminder_settings"

    override fun apply(userId: String, change: SyncController.Change): SyncController.UploadResult {
        val existing = repository.findById(change.id).orElse(null)
        return if (existing == null) {
            if (change.baseVersion != 0L) {
                return SyncController.UploadResult(change.id, status = "conflict", serverVersion = 0L)
            }
            repository.save(toEntity(change, userId, version = 1L))
            SyncController.UploadResult(change.id, status = "ok", serverVersion = 1L)
        } else {
            if (existing.userId != userId) {
                return SyncController.UploadResult(change.id, status = "forbidden", serverVersion = 0L)
            }
            if (change.baseVersion != existing.version) {
                return SyncController.UploadResult(
                    id = change.id,
                    status = "conflict",
                    serverVersion = existing.version,
                    latest = toPayload(existing),
                )
            }
            val merged = toEntity(change, userId, version = existing.version + 1)
            merged.id = existing.id
            merged.createdAt = existing.createdAt
            // nextFireAt 不由客户端控制，重算
            merged.nextFireAt = existing.nextFireAt
            repository.save(merged)
            SyncController.UploadResult(change.id, status = "ok", serverVersion = existing.version + 1)
        }
    }

    override fun toPayload(entity: Any): Map<String, Any?> = toPayload(entity as ReminderSetting)

    fun toPayload(s: ReminderSetting): Map<String, Any?> = mapOf(
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

    private fun toEntity(change: SyncController.Change, userId: String, version: Long): ReminderSetting {
        val type = (change.payload["type"] as? String)
            ?.let { runCatching { com.learnspark.server.domain.entity.ReminderType.valueOf(it.uppercase()) }.getOrNull() }
            ?: com.learnspark.server.domain.entity.ReminderType.CUSTOM
        val repeat = (change.payload["repeatPattern"] as? String)
            ?.let { runCatching { com.learnspark.server.domain.entity.RepeatPattern.valueOf(it.uppercase()) }.getOrNull() }
            ?: com.learnspark.server.domain.entity.RepeatPattern.DAILY
        val time = (change.payload["triggerTime"] as? String)
            ?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
            ?: java.time.LocalTime.of(9, 0)
        return ReminderSetting(
            id = change.id,
            userId = userId,
            type = type,
            title = (change.payload["title"] as? String) ?: "未命名提醒",
            message = change.payload["message"] as? String,
            targetId = change.payload["targetId"] as? String,
            triggerTime = time,
            repeatPattern = repeat,
            weekdayMask = (change.payload["weekdayMask"] as? Number)?.toInt() ?: 127,
            enabled = (change.payload["enabled"] as? Boolean) ?: true,
            version = version,
        )
    }
}
