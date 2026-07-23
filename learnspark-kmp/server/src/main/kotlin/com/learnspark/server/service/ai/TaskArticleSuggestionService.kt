package com.learnspark.server.service.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.learnspark.server.common.ratelimit.UserRateLimiter
import com.learnspark.server.domain.entity.KnowledgeEntry
import com.learnspark.server.domain.entity.TaskArticleLink
import com.learnspark.server.domain.entity.TaskUpload
import com.learnspark.server.domain.repository.KnowledgeEntryRepository
import com.learnspark.server.domain.repository.TaskArticleLinkRepository
import com.learnspark.server.domain.repository.TaskRepository
import com.learnspark.server.domain.repository.TaskUploadRepository
import com.learnspark.server.service.TaskService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * R4c：任务 ↔ 知识库文章 关联建议。
 *
 * 场景：用户规划学习路线时，在某 phase 下的 task 上传了一组笔记 / 文章（落到 knowledge_entries），
 * 然后点 "AI 标注可参考文章"，AI 扫描用户整个知识库，对每篇文章评估"是否对解决该 task 有帮助"，
 * 返回 top-K 推荐（含 reason 解释）。
 *
 * 数据流：
 *   1. 收集 task 的"上下文"：title + description + 已上传的 task_uploads 的解析文本
 *   2. 收集候选知识库条目：用户全部 knowledge_entries（按 updated_at desc 取前 N=30）
 *   3. 调 chat completion，要求返回 JSON 数组 [{entryId, relevance, reason}]
 *   4. 写回 task_article_links（upsert 模式）
 */
@Service
class TaskArticleSuggestionService(
    private val taskService: TaskService,
    private val taskRepository: TaskRepository,
    private val taskUploadRepository: TaskUploadRepository,
    private val knowledgeEntryRepository: KnowledgeEntryRepository,
    private val linkRepository: TaskArticleLinkRepository,
    private val aiConfigService: AiConfigService,
    private val aiClient: OpenAICompatibleClient,
    private val objectMapper: ObjectMapper,
    private val rateLimiter: UserRateLimiter,
) {
    private val log = LoggerFactory.getLogger(TaskArticleSuggestionService::class.java)

    private val candidatePoolSize = 30
    private val maxSuggestions = 8
    private val ratePerMinute = 5

    /**
     * 触发 AI 扫描，写回 task_article_links。
     */
    @Transactional
    fun suggest(userId: String, taskId: String, aiProvider: String? = null): SuggestResult {
        val task = taskService.get(taskId, userId) ?: return SuggestResult.TaskNotFound
        if (!rateLimiter.tryAcquire(userId, permits = 1, perMinute = ratePerMinute)) {
            return SuggestResult.RateLimited
        }
        val provider = aiProvider ?: "deepseek"
        val config = aiConfigService.resolveApiKey(userId, provider) ?: return SuggestResult.NoAiConfig
        // 收集上下文
        val context = buildContext(task.title, task.description, userId, taskId)
        if (context.uploadCount == 0 && context.taskDesc.isBlank()) {
            return SuggestResult.EmptyContext
        }
        val candidates = knowledgeEntryRepository.findByUserIdOrderByUpdatedAtDesc(
            userId,
            org.springframework.data.domain.PageRequest.of(0, candidatePoolSize)
        ).content
        if (candidates.isEmpty()) {
            return SuggestResult.NoCandidates
        }
        val prompt = buildPrompt(task, context, candidates)
        val reply = try {
            aiClient.chatCompletion(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                req = OpenAICompatibleClient.ChatRequest(
                    model = config.model,
                    messages = listOf(
                        OpenAICompatibleClient.Message("system", SYSTEM_PROMPT),
                        OpenAICompatibleClient.Message("user", prompt),
                    ),
                    maxTokens = config.maxTokens,
                    temperature = 0.2,
                ),
            ).choices.firstOrNull()?.message?.content
        } catch (e: Exception) {
            log.warn("TaskArticleSuggestion AI call failed: {}", e.message)
            null
        } ?: return SuggestResult.AiError("empty reply")
        return applySuggestions(taskId, userId, reply, candidates)
    }

    private fun buildContext(
        @Suppress("UNUSED_PARAMETER") title: String,
        description: String?,
        userId: String,
        taskId: String,
    ): TaskContext {
        val uploads = taskUploadRepository.findByTaskIdAndUserIdOrderByCreatedAtDesc(taskId, userId)
        // 把每个 upload 关联的 knowledge_entry 的 content 拼成 "uploads 文本"
        val uploadTexts = uploads.mapNotNull { upload ->
            val entryId = upload.knowledgeEntryId ?: return@mapNotNull null
            val entry = knowledgeEntryRepository.findById(entryId).orElse(null) ?: return@mapNotNull null
            val snippet = (entry.content ?: "").take(800)
            "【${entry.title}】\n${snippet}"
        }.joinToString("\n\n")
        return TaskContext(
            taskDesc = description.orEmpty(),
            uploadTexts = uploadTexts,
            uploadCount = uploads.size,
        )
    }

    private fun buildPrompt(
        task: com.learnspark.server.domain.entity.Task,
        ctx: TaskContext,
        candidates: List<KnowledgeEntry>,
    ): String {
        val taskBlock = buildString {
            append("任务标题：").append(task.title).append("\n")
            if (ctx.taskDesc.isNotBlank()) append("任务描述：").append(ctx.taskDesc).append("\n")
            if (ctx.uploadTexts.isNotBlank()) {
                append("\n本任务已上传的资料片段：\n").append(ctx.uploadTexts.take(2400))
            }
        }
        val candidateBlock = candidates.take(candidatePoolSize).joinToString("\n") { e ->
            val snippet = (e.content ?: "").take(280).replace("\n", " ")
            "- id=${e.id} title=${e.title.take(80)} snippet=${snippet}"
        }
        return """
            请为以下学习任务从候选文章中挑出最相关的 $maxSuggestions 篇。
            $taskBlock

            候选文章（共 ${candidates.size} 篇，按 updatedAt desc 取最近 $candidatePoolSize 篇）：
            $candidateBlock

            严格按 JSON 数组返回：每个元素
            {"entryId":"...","relevance":0-100,"reason":"≤60字中文解释为什么能帮助解决这个任务"}
            - 只挑 relevance >= 40 的文章
            - entryId 必须从候选列表选；reason 简洁
        """.trimIndent()
    }

    private fun applySuggestions(
        taskId: String,
        userId: String,
        reply: String,
        candidates: List<KnowledgeEntry>,
    ): SuggestResult {
        val first = reply.indexOf('[')
        val last = reply.lastIndexOf(']')
        if (first == -1 || last == -1 || last <= first) return SuggestResult.AiError("no json array")
        val validIds = candidates.map { it.id }.toSet()
        return try {
            val node: JsonNode = objectMapper.readTree(reply.substring(first, last + 1))
            val incoming = node.mapNotNull { n ->
                val entryId = n.path("entryId").asText(null) ?: return@mapNotNull null
                if (entryId !in validIds) return@mapNotNull null
                val relevance = n.path("relevance").asInt(50).coerceIn(0, 100)
                val reason = n.path("reason").asText("").take(500)
                if (relevance < 40) return@mapNotNull null
                Triple(entryId, relevance, reason)
            }
            val kept = incoming.take(maxSuggestions)
            // upsert：相同 (taskId, entryId) 覆盖；保留 source = "ai"
            kept.forEach { (entryId, relevance, reason) ->
                val existing = linkRepository.findAll()
                    .firstOrNull { it.taskId == taskId && it.userId == userId && it.entryId == entryId }
                if (existing == null) {
                    linkRepository.save(
                        TaskArticleLink(
                            id = UUID.randomUUID().toString(),
                            taskId = taskId,
                            userId = userId,
                            entryId = entryId,
                            reason = reason,
                            relevance = relevance,
                            source = "ai",
                            version = 1L,
                        )
                    )
                } else {
                    existing.reason = reason
                    existing.relevance = relevance
                    existing.source = "ai"
                    existing.version = existing.version + 1
                    linkRepository.save(existing)
                }
            }
            SuggestResult.Ok(applied = kept.size)
        } catch (e: Exception) {
            SuggestResult.AiError("parse failed: ${e.message}")
        }
    }

    @Transactional(readOnly = true)
    fun listLinks(userId: String, taskId: String): List<TaskArticleLink> {
        if (taskService.get(taskId, userId) == null) return emptyList()
        return linkRepository.findByTaskIdAndUserIdOrderByRelevanceDesc(taskId, userId)
    }

    @Transactional
    fun deleteLink(userId: String, taskId: String, entryId: String): Boolean {
        if (taskService.get(taskId, userId) == null) return false
        return linkRepository.deleteByTaskIdAndUserIdAndEntryId(taskId, userId, entryId) > 0
    }

    @Transactional
    fun upsertManualLink(userId: String, taskId: String, entryId: String, reason: String): UpsertResult {
        if (taskService.get(taskId, userId) == null) return UpsertResult.TaskNotFound
        if (knowledgeEntryRepository.findById(entryId).orElse(null)?.userId != userId) {
            return UpsertResult.EntryNotFound
        }
        val existing = linkRepository.findAll()
            .firstOrNull { it.taskId == taskId && it.userId == userId && it.entryId == entryId }
        if (existing == null) {
            linkRepository.save(
                TaskArticleLink(
                    id = UUID.randomUUID().toString(),
                    taskId = taskId,
                    userId = userId,
                    entryId = entryId,
                    reason = reason.take(500),
                    relevance = 100,
                    source = "manual",
                    version = 1L,
                )
            )
        } else {
            existing.reason = reason.take(500)
            existing.source = "manual"
            existing.version = existing.version + 1
            linkRepository.save(existing)
        }
        return UpsertResult.Ok
    }

    private data class TaskContext(
        val taskDesc: String,
        val uploadTexts: String,
        val uploadCount: Int,
    )

    sealed class SuggestResult {
        data class Ok(val applied: Int) : SuggestResult()
        data object TaskNotFound : SuggestResult()
        data object RateLimited : SuggestResult()
        data object NoAiConfig : SuggestResult()
        data object EmptyContext : SuggestResult()
        data object NoCandidates : SuggestResult()
        data class AiError(val reason: String) : SuggestResult()
    }

    sealed class UpsertResult {
        data object Ok : UpsertResult()
        data object TaskNotFound : UpsertResult()
        data object EntryNotFound : UpsertResult()
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "你是一位严格的学习路径规划助手。回复必须是合法 JSON 数组，不要包含任何额外文字或 markdown 标记。"
    }
}
