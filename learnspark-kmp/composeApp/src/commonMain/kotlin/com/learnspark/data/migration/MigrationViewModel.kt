package com.learnspark.data.migration

import com.learnspark.data.api.LearnSparkApi
import com.learnspark.data.api.MigrationResult
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 阶段 3.1：迁移状态机。
 *
 * 状态流转：
 *   Idle → Detecting → NotFound | Found → Importing → Done(skipped, inserted) | Error
 */
class MigrationViewModel(
    private val service: MigrationService,
    private val api: LearnSparkApi,
    private val settings: Settings,
) {
    private val _state = MutableStateFlow<MigrationState>(MigrationState.Idle)
    val state: StateFlow<MigrationState> = _state.asStateFlow()

    private val keyMigrated = "migration.completed"
    // commonMain 没有 Dispatchers.Main（需 swing/android 扩展），用 Default
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun detect() {
        _state.value = MigrationState.Detecting
        val has = service.hasLegacyExport()
        _state.value = if (has) MigrationState.Found else MigrationState.NotFound
    }

    fun import() {
        scope.launch {
            _state.value = MigrationState.Importing
            try {
                val result = service.importIfPresent()
                if (result != null) {
                    settings.putBoolean(keyMigrated, true)
                    _state.value = MigrationState.Done(result)
                } else {
                    _state.value = MigrationState.NotFound
                }
            } catch (e: Exception) {
                _state.value = MigrationState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun dismiss() {
        _state.value = MigrationState.Idle
    }

    fun isAlreadyMigrated(): Boolean = settings.getBoolean(keyMigrated, false)

    // 关闭 API
    fun release() {
        runCatching { api.close() }
    }
}

sealed class MigrationState {
    data object Idle : MigrationState()
    data object Detecting : MigrationState()
    data object NotFound : MigrationState()
    data object Found : MigrationState()
    data object Importing : MigrationState()
    data class Done(val result: MigrationResult) : MigrationState()
    data class Error(val message: String) : MigrationState()
}
