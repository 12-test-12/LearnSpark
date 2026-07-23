package com.learnspark.server.service.ai

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

/**
 * R4a：通用 OpenAI 兼容协议客户端。
 *
 * 适用于所有遵循 chat-completions JSON 协议的 provider：
 *   - DeepSeek (https://api.deepseek.com/v1)
 *   - OpenAI  (https://api.openai.com/v1)
 *   - Qwen DashScope (https://dashscope.aliyuncs.com/compatible-mode/v1)
 *   - 智谱 GLM (https://open.bigmodel.cn/api/paas/v4)
 *   - Moonshot (https://api.moonshot.cn/v1)
 *   - 自定义 OpenAI 兼容端点
 *
 * 为什么不用各家官方 SDK：
 *   - OpenAI / DeepSeek / Qwen 等 SDK 各异，依赖膨胀
 *   - chat-completions 协议相同，HTTP POST 足矣
 *   - 第三方代理（OneAPI / NewAPI / OpenRouter）也兼容
 *
 * 调用方负责：限流、计费、缓存 —— 客户端只负责 HTTP
 */
@Service
class OpenAICompatibleClient(
    private val props: AiClientProperties,
) {
    private val log = LoggerFactory.getLogger(OpenAICompatibleClient::class.java)

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(props.timeoutSeconds.toLong()))
                setReadTimeout(Duration.ofSeconds(props.timeoutSeconds.toLong()))
            }
        )
        .build()

    /**
     * 发送 chat completion 请求。
     *
     * @param apiKey 明文 API key
     * @param baseUrl 端点（末尾已含 /v1，方法内会拼 /chat/completions）
     * @param req 请求体
     * @throws AiClientException 调用失败
     */
    fun chatCompletion(apiKey: String, baseUrl: String?, req: ChatRequest): ChatResponse {
        val url = (baseUrl?.trimEnd('/') ?: props.defaultBaseUrl) + "/chat/completions"
        log.info("AI chat: model={} url={} messages={}", req.model, url, req.messages.size)
        return try {
            client.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(ChatResponse::class.java)
                ?: throw AiClientException("empty response")
        } catch (e: RestClientResponseException) {
            val msg = e.responseBodyAsString.take(500)
            log.warn("AI HTTP {}: {}", e.statusCode, msg)
            throw AiClientException("HTTP ${e.statusCode}: $msg")
        } catch (e: AiClientException) {
            throw e
        } catch (e: Exception) {
            log.warn("AI call failed: {}", e.message)
            throw AiClientException(e.message ?: "unknown error")
        }
    }

    // === DTOs ===

    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        @JsonProperty("max_tokens") val maxTokens: Int? = null,
        val temperature: Double? = null,
    )

    data class Message(
        val role: String,            // system / user / assistant
        val content: String,
    )

    data class ChatResponse(
        val id: String? = null,
        val model: String? = null,
        val choices: List<Choice> = emptyList(),
        val usage: Usage? = null,
    )

    data class Choice(
        val index: Int = 0,
        val message: Message? = null,
        @JsonProperty("finish_reason") val finishReason: String? = null,
    )

    data class Usage(
        @JsonProperty("prompt_tokens") val promptTokens: Int = 0,
        @JsonProperty("completion_tokens") val completionTokens: Int = 0,
        @JsonProperty("total_tokens") val totalTokens: Int = 0,
    )

    class AiClientException(message: String) : RuntimeException(message)
}
