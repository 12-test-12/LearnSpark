package com.learnspark.server.service

import com.learnspark.server.domain.entity.KnowledgeEntry
import com.learnspark.server.domain.repository.KnowledgeEntryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 阶段 2.4：知识库业务服务。
 *
 * - list: 分页查询（按 updatedAt DESC）
 * - create: 手动创建
 * - get: 详情
 * - update: 更新（version+1 用于冲突副本同步）
 * - delete: 软删除（updatedAt 标记）
 */
@Service
class KnowledgeService(
    private val repository: KnowledgeEntryRepository,
) {

    fun list(userId: String, pageable: Pageable): Page<KnowledgeEntry> =
        repository.findByUserIdOrderByUpdatedAtDesc(userId, pageable)

    /**
     * 阶段 2.4：按标签过滤。
     * tag 为空时退化为按用户全量。
     */
    fun listByTag(userId: String, tag: String?, pageable: Pageable): Page<KnowledgeEntry> =
        repository.findByUserIdAndOptionalTag(userId, tag, pageable)

    /**
     * 阶段 2.4：MySQL FULLTEXT 全文检索。
     */
    fun search(userId: String, query: String, pageable: Pageable): Page<KnowledgeEntry> {
        // ngram 解析器要求 query 长度 >= ngram_token_size（默认 2）
        // 太短的查询会返回空，提示调用方
        require(query.isNotBlank()) { "query must not be blank" }
        return repository.fulltextSearch(userId, query, pageable)
    }

    fun get(id: String): KnowledgeEntry? = repository.findById(id).orElse(null)

    @Transactional
    fun create(
        userId: String,
        title: String,
        content: String?,
        tags: String? = null,
        sourceType: KnowledgeEntry.SourceType = KnowledgeEntry.SourceType.MANUAL,
    ): KnowledgeEntry {
        val entry = KnowledgeEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            content = content,
            tags = tags,
            sourceType = sourceType,
            parseStatus = KnowledgeEntry.ParseStatus.READY,
        )
        return repository.save(entry)
    }

    /**
     * 阶段 3.1：旧版数据迁移专用 create。
     * 允许指定 id（保留旧版 ID）+ sourceType 字符串（兼容旧版大写枚举值）。
     */
    @Transactional
    fun createLegacy(
        id: String,
        userId: String,
        title: String,
        content: String?,
        sourceType: String? = "MANUAL",
        sourcePath: String? = null,
        fileSize: Long? = null,
        fileType: String? = null,
        tags: String? = null,
    ): KnowledgeEntry {
        val resolvedType = runCatching { KnowledgeEntry.SourceType.valueOf(sourceType ?: "MANUAL") }
            .getOrDefault(KnowledgeEntry.SourceType.MANUAL)
        val entry = KnowledgeEntry(
            id = id,
            userId = userId,
            title = title,
            content = content,
            sourcePath = sourcePath,
            fileSize = fileSize,
            fileType = fileType,
            sourceType = resolvedType,
            parseStatus = KnowledgeEntry.ParseStatus.READY,
            tags = tags,
        )
        return repository.save(entry)
    }

    @Transactional
    fun update(
        id: String,
        title: String? = null,
        content: String? = null,
        tags: String? = null,
        baseVersion: Long? = null,
    ): UpdateResult {
        val entry = repository.findById(id).orElse(null)
            ?: return UpdateResult.NotFound

        // 乐观锁：version 不匹配 → 409
        if (baseVersion != null && entry.version != baseVersion) {
            return UpdateResult.VersionConflict(currentVersion = entry.version, current = entry)
        }

        title?.let { entry.title = it }
        content?.let { entry.content = it }
        tags?.let { entry.tags = it }
        entry.version = entry.version + 1
        // updatedAt 由 MySQL ON UPDATE 自动维护
        repository.save(entry)
        return UpdateResult.Ok(entry)
    }

    @Transactional
    fun delete(id: String): Boolean {
        val entry = repository.findById(id).orElse(null) ?: return false
        entry.version = entry.version + 1
        // 软删除：把 content 清空，title 标记 [deleted]
        entry.content = null
        entry.title = "[deleted] ${entry.title}"
        repository.save(entry)
        return true
    }

    sealed class UpdateResult {
        data class Ok(val entry: KnowledgeEntry) : UpdateResult()
        data object NotFound : UpdateResult()
        data class VersionConflict(val currentVersion: Long, val current: KnowledgeEntry) : UpdateResult()
    }
}
