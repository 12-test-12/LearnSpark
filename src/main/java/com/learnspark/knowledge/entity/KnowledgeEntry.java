package com.learnspark.knowledge.entity;

import com.learnspark.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 知识条目实体，对应 knowledge_entries 表。
 *
 * <p>来源三种：upload（文件上传）、submission（任务提交）、manual（手动创建）。
 * 支持软删除（deleted_at），向量字段 vector_embedding 暂以 JSON 存储。
 */
@Entity
@Table(name = "knowledge_entries")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeEntry extends BaseEntity {

    @Id
    @Column(name = "id", updatable = false, columnDefinition = "VARCHAR(36)")
    private String id;

    @Column(name = "user_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String userId;

    @Column(name = "project_id", columnDefinition = "VARCHAR(36)")
    private String projectId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "content_md", columnDefinition = "MEDIUMTEXT")
    private String contentMd;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** upload / submission / manual */
    @Column(name = "source_type", length = 50)
    private String sourceType;

    /** 来源 ID（如 submission.id） */
    @Column(name = "source_id", columnDefinition = "VARCHAR(36)")
    private String sourceId;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** 标签列表，存为 JSON 数组 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "JSON")
    private List<String> tags;

    @Column(name = "word_count")
    private Integer wordCount;

    /** pending / parsing / done / failed */
    @Column(name = "parse_status", length = 20)
    private String parseStatus;

    /** 向量嵌入（1536 维 float 数组），暂以 JSON 存储 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vector_embedding", columnDefinition = "JSON")
    private List<Double> vectorEmbedding;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void fillDefaults() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.wordCount == null) {
            this.wordCount = 0;
        }
        if (this.parseStatus == null) {
            this.parseStatus = "done";
        }
    }
}
