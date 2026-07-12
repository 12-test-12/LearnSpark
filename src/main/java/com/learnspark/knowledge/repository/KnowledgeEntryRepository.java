package com.learnspark.knowledge.repository;

import com.learnspark.knowledge.entity.KnowledgeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识条目仓库（自动过滤已软删除记录）。
 */
@Repository
public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, String> {

    /** 按用户查询所有知识条目（按创建时间倒序） */
    List<KnowledgeEntry> findByUserIdOrderByCreatedAtDesc(String userId);

    /** 按来源查询（如某次提交对应的知识条目） */
    Optional<KnowledgeEntry> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    /** 按用户 + 项目查询 */
    List<KnowledgeEntry> findByUserIdAndProjectIdOrderByCreatedAtDesc(String userId, String projectId);

    /** 按用户 + 标题查询（用于 [[双向链接]] 匹配目标条目） */
    Optional<KnowledgeEntry> findByUserIdAndTitle(String userId, String title);

    /** 统计用户知识库条目数（用于 kb_10 徽章与仪表盘统计） */
    long countByUserId(String userId);

    /**
     * 查询内容中包含指定 [[wikilink]] 文本的所有条目（用于新建条目时回扫反链）。
     *
     * <p>利用 @SQLRestriction 自动过滤已删除条目。
     * pattern 应为 {@code [[链接文本]]} 格式。
     */
    @Query("SELECT e FROM KnowledgeEntry e WHERE e.userId = :userId AND " +
           "(e.contentMd LIKE %:pattern% OR e.content LIKE %:pattern%)")
    List<KnowledgeEntry> findEntriesContainingWikilink(@Param("userId") String userId,
                                                       @Param("pattern") String pattern);

    /**
     * MySQL FULLTEXT 全文检索（ngram 分词器，支持中文）。
     *
     * <p>注意：native query 不受 {@code @SQLRestriction} 影响，需手动过滤 deleted_at。
     */
    @Query(value = "SELECT * FROM knowledge_entries " +
            "WHERE user_id = :userId AND deleted_at IS NULL " +
            "AND MATCH(title, content) AGAINST(:q IN NATURAL LANGUAGE MODE)",
            countQuery = "SELECT COUNT(*) FROM knowledge_entries " +
                    "WHERE user_id = :userId AND deleted_at IS NULL " +
                    "AND MATCH(title, content) AGAINST(:q IN NATURAL LANGUAGE MODE)",
            nativeQuery = true)
    Page<KnowledgeEntry> searchByFulltext(@Param("userId") String userId,
                                          @Param("q") String query,
                                          Pageable pageable);
}
