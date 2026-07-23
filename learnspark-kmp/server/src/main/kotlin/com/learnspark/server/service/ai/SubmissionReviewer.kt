package com.learnspark.server.service.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.learnspark.server.domain.repository.SubmissionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * R2：Submission AI 评审器。
 *
 * 调用 DeepSeek chat-completion，prompt 固定模板要求返回 JSON：
 *   {"score": 0-100, "feedback": "string", "highlights": ["...","..."]}
 * 用正则 / Jackson 解析；解析失败时降级为 score=0 + error message。
 *
 * 为什么不直接用 DeepSeek function-calling：
 * - OpenAI 兼容的 function-calling 不同 provider 字段名有差异
 * - 用纯 prompt 约束输出 JSON 兼容性更好
 */
@Service
class SubmissionReviewer(
    private val submissionRepository: SubmissionRepository,
    private val aiConfigService: AiConfigService,
    private val aiClient: OpenAICompatibleClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(SubmissionReviewer::class.java)

    @Transactional
    fun review(submissionId: String, userId: String) {
        val submission = submissionRepository.findById(submissionId).orElse(null) ?: run {
            log.warn("submission {} not found", submissionId)
            return
        }
        if (submission.userId != userId) {
            log.warn("submission {} ownership mismatch", submissionId)
            return
        }
        val config = aiConfigService.resolveApiKey(userId, "deepseek")
        if (config == null) {
            log.info("user {} has no enabled DeepSeek config, skip review", userId)
            return
        }
        val now = Instant.now()
        try {
            val prompt = buildPrompt(submission.content)
            val resp = aiClient.chatCompletion(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                req = OpenAICompatibleClient.ChatRequest(
                    model = config.model,
                    messages = listOf(
                        OpenAICompatibleClient.Message("system", SYSTEM_PROMPT),
                        OpenAICompatibleClient.Message("user", prompt),
                    ),
                    maxTokens = config.maxTokens,
                    temperature = config.temperature,
                ),
            )
            val reply = resp.choices.firstOrNull()?.message?.content
                ?: throw OpenAICompatibleClient.AiClientException("empty reply")
            applyReviewResult(submission, reply)
        } catch (e: Exception) {
            log.warn("AI review failed submission={}: {}", submissionId, e.message)
            // 失败也要写 reviewed_at + 错误信息，让客户端能区分"未评审"和"评审失败"
            submission.aiScore = 0
            submission.aiFeedback = "AI 评审失败：${e.message}"
            submission.aiHighlights = null
            submission.reviewedAt = now
            submission.version = submission.version + 1
            submissionRepository.save(submission)
        }
    }

    /**
     * 从 DeepSeek 返回的纯文本里提取 JSON。
     * 防御：DeepSeek 偶尔在 JSON 外加 ```json 块或解释性前缀
     */
    private fun applyReviewResult(submission: com.learnspark.server.domain.entity.Submission, reply: String) {
        val jsonText = extractJson(reply) ?: run {
            submission.aiScore = 0
            submission.aiFeedback = "AI 评审解析失败：$reply".take(2000)
            submission.aiHighlights = null
            submission.reviewedAt = Instant.now()
            submission.version = submission.version + 1
            submissionRepository.save(submission)
            return
        }
        try {
            val node: JsonNode = objectMapper.readTree(jsonText)
            val score = node.path("score").asInt(0).coerceIn(0, 100)
            val feedback = node.path("feedback").asText("").take(2000)
            val highlights = node.path("highlights").takeIf { it.isArray }
                ?.let { arr -> arr.mapNotNull { it.asText(null) }.joinToString("\n").take(2000) }
            submission.aiScore = score
            submission.aiFeedback = feedback
            submission.aiHighlights = highlights
            submission.reviewedAt = Instant.now()
            submission.version = submission.version + 1
            submissionRepository.save(submission)
            log.info("submission {} reviewed score={}", submission.id, score)
        } catch (e: Exception) {
            submission.aiScore = 0
            submission.aiFeedback = "JSON 解析失败：${e.message}"
            submission.reviewedAt = Instant.now()
            submission.version = submission.version + 1
            submissionRepository.save(submission)
        }
    }

    private fun extractJson(text: String): String? {
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) return null
        return text.substring(firstBrace, lastBrace + 1)
    }

    private fun buildPrompt(content: String): String =
        "请评审以下学习任务提交，给出 0-100 评分、改进建议、亮点提取。\n\n" +
            "```\n${content.take(8000)}\n```\n\n" +
            "严格按 JSON 格式返回：\n" +
            "{\"score\": 0, \"feedback\": \"...\", \"highlights\": [\"...\",\"...\"]}"

    companion object {
        private const val SYSTEM_PROMPT =
            "你是一位严格但有建设性的学习评审者。回复必须是 JSON，无任何额外文字。"
    }
}
