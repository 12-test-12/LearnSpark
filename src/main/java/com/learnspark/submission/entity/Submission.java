package com.learnspark.submission.entity;

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
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 任务提交记录实体，对应 submissions 表。
 *
 * <p>记录用户对某次任务的提交内容以及 AI 审核结果。
 * 一次提交对应一条记录；未通过可再次提交（生成新记录）。
 */
@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission extends BaseEntity {

    @Id
    @Column(name = "id", updatable = false, columnDefinition = "VARCHAR(36)")
    private String id;

    @Column(name = "task_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String taskId;

    @Column(name = "user_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String userId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 附件 URL 列表，存为 JSON 数组 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_urls", columnDefinition = "JSON")
    private List<String> attachmentUrls;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(name = "ai_score", columnDefinition = "SMALLINT")
    private Integer aiScore;

    @Column(name = "passed", columnDefinition = "TINYINT(1)")
    private Boolean passed;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    /** AI 原始响应，存为 JSON 对象 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_raw_response", columnDefinition = "JSON")
    private Map<String, Object> aiRawResponse;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    void fillDefaults() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.submittedAt == null) {
            this.submittedAt = LocalDateTime.now();
        }
    }
}
