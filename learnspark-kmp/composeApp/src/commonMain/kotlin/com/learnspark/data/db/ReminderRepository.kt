package com.learnspark.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.learnspark.data.model.ReminderLogDto
import com.learnspark.data.model.ReminderSettingDto
import com.learnspark.db.LearnSparkDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * R3b：提醒仓库（settings + logs）。
 *
 * Settings 是双向同步（本地编辑 → push / pull 拉服务端最新为准）。
 * Logs 是只读缓存（仅 UI 显示与已读状态机）。
 */
class ReminderRepository(
    private val db: LearnSparkDb,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observeSettings(): Flow<List<ReminderSettingDto>> =
        db.reminderSettingQueries.selectAllReminderSettings()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::settingToDto) }

    suspend fun getAllSettings(): List<ReminderSettingDto> =
        db.reminderSettingQueries.selectAllReminderSettings().executeAsList().map(::settingToDto)

    suspend fun upsertSetting(dto: ReminderSettingDto, isDirty: Boolean = false) {
        db.reminderSettingQueries.upsertReminderSetting(
            id = dto.id,
            userId = dto.userId,
            type = dto.type,
            title = dto.title,
            message = dto.message,
            targetId = dto.targetId,
            triggerTime = dto.triggerTime,
            repeatPattern = dto.repeatPattern,
            weekdayMask = dto.weekdayMask.toLong(),
            nextFireAt = dto.nextFireAt,
            enabled = if (dto.enabled) 1L else 0L,
            serverVersion = dto.version,
            localVersion = dto.version,
            isDirty = if (isDirty) 1L else 0L,
            createdAt = dto.createdAt ?: isoNow(),
            updatedAt = dto.updatedAt ?: isoNow(),
        )
    }

    suspend fun deleteSetting(id: String) {
        db.reminderSettingQueries.deleteReminderSettingById(id)
    }

    // === Logs ===

    fun observePendingLogs(): Flow<List<ReminderLogDto>> =
        db.reminderLogQueries.selectPendingReminderLogs()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::logToDto) }

    fun observeAllLogs(): Flow<List<ReminderLogDto>> =
        db.reminderLogQueries.selectAllReminderLogs()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::logToDto) }

    suspend fun upsertLog(dto: ReminderLogDto) {
        db.reminderLogQueries.upsertReminderLog(
            id = dto.id,
            userId = dto.userId,
            settingId = dto.settingId,
            firedAt = dto.firedAt,
            title = dto.title,
            message = dto.message,
            targetId = dto.targetId,
            acknowledged = if (dto.acknowledged) 1L else 0L,
        )
    }

    suspend fun acknowledgeLog(id: String) {
        db.reminderLogQueries.acknowledgeReminderLog(id)
    }

    suspend fun acknowledgeAllLogs() {
        db.reminderLogQueries.acknowledgeAllReminderLogs()
    }

    private fun settingToDto(row: com.learnspark.db.ReminderSetting) = ReminderSettingDto(
        id = row.id,
        userId = row.userId,
        type = row.type,
        title = row.title,
        message = row.message,
        targetId = row.targetId,
        triggerTime = row.triggerTime,
        repeatPattern = row.repeatPattern,
        weekdayMask = row.weekdayMask.toInt(),
        nextFireAt = row.nextFireAt,
        enabled = row.enabled == 1L,
        version = row.serverVersion,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun logToDto(row: com.learnspark.db.ReminderLog) = ReminderLogDto(
        id = row.id,
        userId = row.userId,
        settingId = row.settingId,
        firedAt = row.firedAt,
        title = row.title,
        message = row.message,
        targetId = row.targetId,
        acknowledged = row.acknowledged == 1L,
    )

    private fun isoNow(): String {
        val instant = java.time.Instant.ofEpochMilli(clock())
        return java.time.OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC).toString()
    }
}
