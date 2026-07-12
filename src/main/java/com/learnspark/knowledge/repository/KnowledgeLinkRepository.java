package com.learnspark.knowledge.repository;

import com.learnspark.knowledge.entity.KnowledgeLink;
import com.learnspark.knowledge.entity.KnowledgeLinkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识库双向链接仓库。
 */
@Repository
public interface KnowledgeLinkRepository extends JpaRepository<KnowledgeLink, KnowledgeLinkId> {

    /** 查询某条笔记的所有出链（source → targets） */
    List<KnowledgeLink> findBySourceEntryId(String sourceEntryId);

    /** 查询某条笔记的所有反链（sources → target） */
    List<KnowledgeLink> findByTargetEntryId(String targetEntryId);
}
