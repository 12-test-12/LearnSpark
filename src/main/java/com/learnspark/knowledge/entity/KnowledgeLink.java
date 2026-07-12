package com.learnspark.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库双向链接实体，对应 knowledge_links 表。
 *
 * <p>记录笔记之间的 {@code [[双向链接]]} 关系，source → target。
 * 使用 {@code @IdClass} 支持复合主键。
 */
@Entity
@Table(name = "knowledge_links")
@IdClass(KnowledgeLinkId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeLink {

    @Id
    @Column(name = "source_entry_id")
    private String sourceEntryId;

    @Id
    @Column(name = "target_entry_id")
    private String targetEntryId;

    @Column(name = "link_text", length = 500)
    private String linkText;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void fillDefaults() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
