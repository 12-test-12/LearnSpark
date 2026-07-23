package com.learnspark.features.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.learnspark.data.api.LearnSparkApi
import com.learnspark.data.model.AiConfigDto
import com.learnspark.data.model.AiProviderDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * R4a：AI 配置 ViewModel。
 *
 * 状态：
 *   - providers: 服务商元数据列表
 *   - configs: 用户已配置的 AI 通道
 *   - ui.loading / ui.error
 */
class AiConfigViewModel(
    private val api: LearnSparkApi,
) : ScreenModel {

    private val _providers = MutableStateFlow<List<AiProviderDto>>(emptyList())
    val providers: StateFlow<List<AiProviderDto>> = _providers.asStateFlow()

    private val _configs = MutableStateFlow<List<AiConfigDto>>(emptyList())
    val configs: StateFlow<List<AiConfigDto>> = _configs.asStateFlow()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        screenModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val userId = defaultUserId()
                val providers = api.listAiProviders().map(::toProviderDto)
                val configs = api.listAiConfigs(userId).map(::toConfigDto)
                _providers.value = providers
                _configs.value = configs
            } catch (e: Exception) {
                _ui.update { it.copy(error = "加载失败：${e.message}") }
            } finally {
                _ui.update { it.copy(loading = false) }
            }
        }
    }

    fun upsertConfig(provider: String, apiKey: String, model: String, baseUrl: String?) {
        screenModelScope.launch {
            try {
                val userId = defaultUserId()
                api.upsertAiConfig(
                    userId = userId,
                    provider = provider,
                    apiKey = apiKey,
                    model = model,
                    baseUrl = baseUrl,
                )
                refresh()
            } catch (e: Exception) {
                _ui.update { it.copy(error = "保存失败：${e.message}") }
            }
        }
    }

    fun deleteConfig(id: String) {
        screenModelScope.launch {
            try {
                val userId = defaultUserId()
                api.deleteAiConfig(userId, id)
                refresh()
            } catch (e: Exception) {
                _ui.update { it.copy(error = "删除失败：${e.message}") }
            }
        }
    }

    fun dismissError() {
        _ui.update { it.copy(error = null) }
    }

    fun goBack() {
        // 由父 Screen 自行处理（ScreenModel 不直接持有 Navigator）
    }

    data class UiState(val loading: Boolean = false, val error: String? = null)

    private fun toProviderDto(m: Map<String, Any?>): AiProviderDto = AiProviderDto(
        id = m["id"] as? String ?: "",
        displayName = m["displayName"] as? String ?: m["id"]?.toString() ?: "",
        defaultBaseUrl = m["defaultBaseUrl"] as? String ?: "",
        defaultModel = m["defaultModel"] as? String ?: "",
        popularModels = (m["popularModels"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        apiKeyHint = m["apiKeyHint"] as? String ?: "",
        docsUrl = m["docsUrl"] as? String,
    )

    private fun toConfigDto(m: Map<String, Any?>): AiConfigDto = AiConfigDto(
        id = m["id"] as? String ?: "",
        userId = m["userId"] as? String ?: "",
        provider = m["provider"] as? String ?: "",
        apiKeyMasked = m["apiKeyMasked"] as? String ?: "****",
        model = m["model"] as? String ?: "",
        baseUrl = m["baseUrl"] as? String,
        maxTokens = (m["maxTokens"] as? Number)?.toInt() ?: 2048,
        temperature = (m["temperature"] as? Number)?.toDouble() ?: 0.7,
        enabled = (m["enabled"] as? Boolean) ?: true,
        version = (m["version"] as? Number)?.toLong() ?: 0,
    )
}

private fun defaultUserId(): String = "00000000-0000-0000-0000-000000000001"
