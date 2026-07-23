package com.learnspark.server.service.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.learnspark.server.common.ratelimit.UserRateLimiter
import com.learnspark.server.domain.entity.KnowledgeEntry
import com.learnspark.server.domain.entity.KnowledgeFolder
import com.learnspark.server.domain.repository.KnowledgeEntryRepository
import com.learnspark.server.domain.repository.KnowledgeFolderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * R3c：AI 整理知识库。
 *
 * 流程：
 * 1) 拿用户全部 folder（路径化扁平）
 * 2) 拿指定 entryIds（或全部未分类）的内容摘要
 * 3) 调 DeepSeek 一次性建议 entry → folderId（或建议新建子目录）
 * 4) 返回建议列表，客户端确认后批量落库
 *
 * 限流：5/min（与 AiConfigService 共享 UserRateLimiter 配额）
 */
@Service
class KnowledgeOrganizeService(
    private val entryRepository: KnowledgeEntryRepository,
    private val folderRepository: KnowledgeFolderRepository,
    private val aiConfigService: AiConfigService,
    private val aiClient: OpenAICompatibleClient,
    private val objectMapper: ObjectMapper,
    private val rateLimiter: UserRateLimiter,
) {
    private val log = LoggerFactory.getLogger(KnowledgeOrganizeService::class.java)

    /**
     * 返回每个 entry 的建议结果（不落库）。
     */
    @Transactional(readOnly = true)
    fun suggest(userId: String, entryIds: List<String>?): OrganizeResult {
        if (!rateLimiter.tryAcquire(userId, permits = 1, perMinute = 5)) {
            return OrganizeResult.RateLimited
        }
        val config = aiConfigService.resolveApiKey(userId, "deepseek")
            ?: return OrganizeResult.NoAiConfig

        val folders = folderRepository.findByUserId(userId)
        if (folders.isEmpty()) {
            return OrganizeResult.NoFolders
        }

        val entries = if (entryIds.isNullOrEmpty()) {
            // 默认：取最近 30 条未分类或全部
            entryRepository.findByUserIdOrderByUpdatedAtDesc(
                userId,
                org.springframework.data.domain.PageRequest.of(0, 30),
            ).content
        } else {
            entryIds.mapNotNull { entryRepository.findById(it).orElse(null) }
                .filter { it.userId == userId }
        }
        if (entries.isEmpty()) {
            return OrganizeResult.NoEntries
        }

        val prompt = buildPrompt(folders, entries)
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
                    log.warn("organize ai call failed: {}", e.message)
                    null
                } ?: return OrganizeResult.AiError("empty reply")

        return parseReply(reply, folders)
    }

    /**
     * 把用户接受的建议批量落库（仅落 entries.folderId，不自动新建目录）
     */
    @Transactional
    fun apply(userId: String, acceptances: List<Acceptance>): ApplyResult {
        var applied = 0
        var failed = 0
        acceptances.forEach { acc ->
            val entry = entryRepository.findById(acc.entryId).orElse(null)
            if (entry == null || entry.userId != userId) {
                failed += 1
                return@forEach
            }
            if (acc.folderId != null) {
                val folder = folderRepository.findByIdAndUserId(acc.folderId, userId)
                if (folder == null) {
                    failed += 1
                    return@forEach
                }
                entry.folderId = acc.folderId
            } else {
                entry.folderId = null
            }
            entry.version = entry.version + 1
            entryRepository.save(entry)
            applied += 1
        }
        return ApplyResult(applied = applied, failed = failed)
    }

    // === Prompt & parse ===

    private fun buildPrompt(folders: List<KnowledgeFolder>, entries: List<KnowledgeEntry>): String {
        val folderList = folders.joinToString("\n") { f ->
            "- id=${f.id} path=${f.path} name=${f.name}"
        }
        val entryList = entries.take(50).joinToString("\n") { e ->
            "- id=${e.id} title=${e.title.take(60)} summary=${(e.content ?: "").take(160).replace("\n", " ")}"
        }
        return """
            请为以下知识条目建议所属文件夹。已有目录列表：
            $folderList

            知识条目：
            $entryList

            严格按 JSON 数组返回：每个元素 {"entryId": "...", "folderId": "..."或 null, "reason": "..."}
            - folderId 必须从已有目录中选
            - 没有合适目录时填 null（前端会弹出"新建目录"对话框）
        """.trimIndent()
    }

    private fun parseReply(reply: String, folders: List<KnowledgeFolder>): OrganizeResult {
        val first = reply.indexOf('[')
        val last = reply.lastIndexOf(']')
        if (first == -1 || last == -1 || last <= first) return OrganizeResult.AiError("no json array")
        val validFolderIds = folders.map { it.id }.toSet()
        return try {
            val arr: JsonNode = objectMapper.readTree(reply.substring(first, last + 1))
            val suggestions = arr.mapNotNull { node ->
                val entryId = node.path("entryId").asText(null) ?: return@mapNotNull null
                val folderId = node.path("folderId").asText(null)
                    ?.takeIf { validFolderIds.contains(it) }
                val reason = node.path("reason").asText("").take(200)
                Suggestion(entryId, folderId, reason)
            }
            OrganizeResult.Ok(suggestions)
        } catch (e: Exception) {
            OrganizeResult.AiError("parse failed: ${e.message}")
        }
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "你是一个知识管理助手。回复必须是合法 JSON 数组，不要包含任何额外文字或 markdown 标记。"
    }

    // === DTOs ===

    data class Suggestion(
        val entryId: String,
        val folderId: String?,
        val reason: String,
    )

    data class Acceptance(
        val entryId: String,
        val folderId: String?,
    )

    data class ApplyResult(val applied: Int, val failed: Int)

    sealed class OrganizeResult {
        data class Ok(val suggestions: List<Suggestion>) : OrganizeResult()
        data object RateLimited : OrganizeResult()
        data object NoAiConfig : OrganizeResult()
        data object NoFolders : OrganizeResult()
        data object NoEntries : OrganizeResult()
        data class AiError(val reason: String) : OrganizeResult()
    }
}
