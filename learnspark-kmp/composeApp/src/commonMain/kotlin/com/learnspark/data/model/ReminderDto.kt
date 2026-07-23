package com.learnspark.data.model

import kotlinx.serialization.Serializable

/**
 * R3b：提醒规则 DTO。
 */
@Serializable
data class ReminderSettingDto(
    val id: String,
    val userId: String,
    val type: String,                 // TASK_DUE / CHECK_IN / CUSTOM
    val title: String,
    val message: String? = null,
    val targetId: String? = null,
    val triggerTime: String,          // "HH:mm:ss"
    val repeatPattern: String,        // ONCE / DAILY / WEEKDAYS / WEEKLY
    val weekdayMask: Int = 127,
    val nextFireAt: String? = null,
    val enabled: Boolean = true,
    val version: Long = 1L,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): ReminderSettingDto = ReminderSettingDto(
            id = map["id"] as? String ?: "",
            userId = map["userId"] as? String ?: "",
            type = map["type"] as? String ?: "CUSTOM",
            title = map["title"] as? String ?: "",
            message = map["message"] as? String,
            targetId = map["targetId"] as? String,
            triggerTime = map["triggerTime"] as? String ?: "09:00:00",
            repeatPattern = map["repeatPattern"] as? String ?: "DAILY",
            weekdayMask = (map["weekdayMask"] as? Number)?.toInt() ?: 127,
            nextFireAt = map["nextFireAt"] as? String,
            enabled = (map["enabled"] as? Boolean) ?: true,
            version = (map["version"] as? Number)?.toLong() ?: 1L,
            createdAt = map["createdAt"] as? String,
            updatedAt = map["updatedAt"] as? String,
        )
    }
}

/**
 * R3b：提醒触发日志 DTO。
 */
@Serializable
data class ReminderLogDto(
    val id: String,
    val userId: String,
    val settingId: String,
    val firedAt: String,
    val title: String,
    val message: String? = null,
    val targetId: String? = null,
    val acknowledged: Boolean = false,
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): ReminderLogDto = ReminderLogDto(
            id = map["id"] as? String ?: "",
            userId = map["userId"] as? String ?: "",
            settingId = map["settingId"] as? String ?: "",
            firedAt = map["firedAt"] as? String ?: "",
            title = map["title"] as? String ?: "",
            message = map["message"] as? String,
            targetId = map["targetId"] as? String,
            acknowledged = (map["acknowledged"] as? Boolean) ?: false,
        )
    }
}
