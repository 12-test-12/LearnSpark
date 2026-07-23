package com.learnspark.server.api

import com.learnspark.server.service.ai.AiProvider
import com.learnspark.server.service.ai.AiProviderRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * R4a：AI provider 元数据查询。
 *
 * GET /api/v1/ai/providers
 *   → { "items": [{ id, displayName, defaultBaseUrl, defaultModel, popularModels, apiKeyHint, docsUrl }] }
 *
 * 客户端 UI 用此列表渲染「选择服务商」下拉框；用户选定后再调用
 * POST /api/v1/ai/configs 提交具体 apiKey + model。
 */
@RestController
@RequestMapping("/api/v1/ai/providers")
class AiProviderController(
    private val registry: AiProviderRegistry,
) {

    @GetMapping
    fun list(): Map<String, Any?> = mapOf(
        "items" to registry.list().map(::toDto)
    )

    private fun toDto(p: AiProvider) = mapOf(
        "id" to p.id,
        "displayName" to p.displayName,
        "defaultBaseUrl" to p.defaultBaseUrl,
        "defaultModel" to p.defaultModel,
        "popularModels" to p.popularModels,
        "apiKeyHint" to p.apiKeyHint,
        "docsUrl" to p.docsUrl,
    )
}
