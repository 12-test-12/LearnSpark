package com.learnspark.features.notification

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.learnspark.data.api.LearnSparkApi
import com.learnspark.data.db.ReminderRepository
import com.learnspark.data.model.ReminderLogDto
import com.learnspark.data.model.ReminderSettingDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * R3b：提醒与通知 ViewModel。
 *
 * 职责：
 * - settings 的 CRUD（远端为主，pull 拉服务端）
 * - logs 拉取 + 平台原生通知触发
 * - 后台轮询：每 60s 拉一次 logs，发现新条目时弹通知
 */
class ReminderViewModel(
    private val api: LearnSparkApi,
    private val repository: ReminderRepository,
    private val notificationManager: NotificationManager,
    private val userId: String = "00000000-0000-0000-0000-000000000001",
    private val notifier: Notifier,
) : ScreenModel {

    val settings: StateFlow<List<ReminderSettingDto>> = repository.observeSettings()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    val pendingLogs: StateFlow<List<ReminderLogDto>> = repository.observePendingLogs()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    private val _ui = MutableStateFlow(ReminderUiState())
    val ui: StateFlow<ReminderUiState> = _ui.asStateFlow()

    init {
        refreshSettings()
        refreshLogs(showNotifications = true)
        startPolling()
    }

    fun refreshSettings() {
        screenModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val remote = api.listReminderSettings(userId)
                remote.forEach { repository.upsertSetting(ReminderSettingDto.fromMap(it)) }
                _ui.value = _ui.value.copy(loading = false)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "unknown")
            }
        }
    }

    fun refreshLogs(showNotifications: Boolean = false) {
        screenModelScope.launch {
            try {
                val remote = api.listReminderLogs(userId, pendingOnly = true)
                val existingIds = repository.getAllSettings().map { it.id }.toSet()
                val allSettings = repository.getAllSettings().associateBy { it.id }
                remote.forEach { map ->
                    val dto = ReminderLogDto.fromMap(map)
                    val prev = repository.observeAllLogs()
                    repository.upsertLog(dto)
                    // 新通知：触发平台原生通知
                    if (showNotifications && !dto.acknowledged && dto.id !in existingIds) {
                        notificationManager.notify(
                            title = dto.title,
                            body = dto.message ?: allSettings[dto.settingId]?.message ?: "您有一条新提醒",
                        )
                    }
                }
                _ui.value = _ui.value.copy(lastPollAt = System.currentTimeMillis())
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "拉取通知失败：${e.message}")
            }
        }
    }

    fun createSetting(
        title: String,
        message: String?,
        triggerTime: String,
        repeatPattern: String,
        weekdayMask: Int,
        enabled: Boolean = true,
    ) {
        screenModelScope.launch {
            try {
                val raw = api.createReminderSetting(
                    userId = userId,
                    title = title,
                    message = message,
                    triggerTime = triggerTime,
                    repeatPattern = repeatPattern,
                    weekdayMask = weekdayMask,
                    enabled = enabled,
                )
                repository.upsertSetting(ReminderSettingDto.fromMap(raw))
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "新建失败：${e.message}")
            }
        }
    }

    fun updateSetting(setting: ReminderSettingDto) {
        screenModelScope.launch {
            try {
                val raw = api.updateReminderSetting(
                    userId = userId,
                    id = setting.id,
                    title = setting.title,
                    message = setting.message,
                    triggerTime = setting.triggerTime,
                    repeatPattern = setting.repeatPattern,
                    weekdayMask = setting.weekdayMask,
                    enabled = setting.enabled,
                )
                repository.upsertSetting(ReminderSettingDto.fromMap(raw))
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "更新失败：${e.message}")
            }
        }
    }

    fun toggleEnabled(setting: ReminderSettingDto, enabled: Boolean) {
        screenModelScope.launch {
            try {
                val raw = api.updateReminderSetting(userId, setting.id, enabled = enabled)
                repository.upsertSetting(ReminderSettingDto.fromMap(raw))
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "切换状态失败：${e.message}")
            }
        }
    }

    fun deleteSetting(setting: ReminderSettingDto) {
        screenModelScope.launch {
            try {
                api.deleteReminderSetting(userId, setting.id)
                repository.deleteSetting(setting.id)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "删除失败：${e.message}")
            }
        }
    }

    fun acknowledge(log: ReminderLogDto) {
        screenModelScope.launch {
            try {
                api.ackReminderLog(userId, log.id)
                repository.acknowledgeLog(log.id)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "确认失败：${e.message}")
            }
        }
    }

    fun acknowledgeAll() {
        screenModelScope.launch {
            try {
                api.ackAllReminderLogs(userId)
                repository.acknowledgeAllLogs()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "清空失败：${e.message}")
            }
        }
    }

    private fun startPolling() {
        screenModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                refreshLogs(showNotifications = true)
            }
        }
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }

    data class ReminderUiState(
        val loading: Boolean = false,
        val error: String? = null,
        val lastPollAt: Long = 0L,
    )
}
