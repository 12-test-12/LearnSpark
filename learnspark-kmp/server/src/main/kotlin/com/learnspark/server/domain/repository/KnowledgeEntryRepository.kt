package com.learnspark.server.domain.repository

import com.learnspark.server.domain.entity.KnowledgeEntry
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface KnowledgeEntryRepository : JpaRepository<KnowledgeEntry, String> {

    fun findByUserIdOrderByUpdatedAtDesc(userId: String, pageable: Pageable): Page<KnowledgeEntry>

    /**
     * 按用户过滤（v1 简化版：不限制 userId 走 DB，依赖 X-User-Id 头层校验）。
     * 阶段 2.4：标签过滤。
     */
    @Query(
        """
        SELECT k FROM KnowledgeEntry k
        WHERE k.userId = :userId
        ORDER BY k.updatedAt DESC
        """
    )
    fun findByUserIdAndOptionalTag(
        @Param("userId") userId: String,
        @Param("tag") tag: String?,
        pageable: Pageable,
    ): Page<KnowledgeEntry>

    /**
     * 阶段 2.4：MySQL FULLTEXT + ngram 全文检索。
     *
     * 注意：FULLTEXT MATCH AGAINST 在 JPQL 中以 nativeQuery 形式表达更直接。
     * 使用 nativeQuery 走 MySQL 原生 MATCH ... AGAINST 语法。
     * booleanMode=true 让 ngram 解析器正常工作（默认 natural language 在中文短词上效果差）。
     */
    @Query(
        value = """
            SELECT * FROM knowledge_entries
            WHERE user_id = :userId
              AND MATCH(title, content) AGAINST (:query IN BOOLEAN MODE)
            ORDER BY updated_at DESC
            LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}
        """,
        countQuery = """
            SELECT COUNT(*) FROM knowledge_entries
            WHERE user_id = :userId
              AND MATCH(title, content) AGAINST (:query IN BOOLEAN MODE)
        """,
        nativeQuery = true,
    )
    fun fulltextSearch(
        @Param("userId") userId: String,
        @Param("query") query: String,
        pageable: Pageable,
    ): Page<KnowledgeEntry>

    @Modifying
    @Query("UPDATE KnowledgeEntry k SET k.content = :content, k.parseStatus = com.learnspark.server.domain.entity.KnowledgeEntry.ParseStatus.READY, k.parseError = null, k.updatedAt = :now, k.version = k.version + 1 WHERE k.id = :id")
    fun markReady(
        @Param("id") id: String,
        @Param("content") content: String,
        @Param("now") now: Instant,
    ): Int

    @Modifying
    @Query("UPDATE KnowledgeEntry k SET k.parseStatus = com.learnspark.server.domain.entity.KnowledgeEntry.ParseStatus.FAILED, k.parseError = :err WHERE k.id = :id")
    fun markFailed(
        @Param("id") id: String,
        @Param("err") err: String,
    ): Int

    @Modifying
    @Query("UPDATE KnowledgeEntry k SET k.parseStatus = com.learnspark.server.domain.entity.KnowledgeEntry.ParseStatus.PROCESSING WHERE k.id = :id AND k.parseStatus = com.learnspark.server.domain.entity.KnowledgeEntry.ParseStatus.PENDING")
    fun markProcessing(@Param("id") id: String): Int

    /**
     * R3a：按 folderId 列出 knowledge 条目。
     */
    fun findByFolderIdAndUserId(folderId: String, userId: String): List<KnowledgeEntry>
}
