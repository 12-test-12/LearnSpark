package com.learnspark.plan.entity;

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
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 学习项目实体，对应 projects 表。
 *
 * <p>按用户隔离（user_id），支持软删除（deleted_at）。
 * 使用 {@code @SQLRestriction} 自动过滤已删除记录。
 */
@Entity
@Table(name = "projects")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity {

    @Id
    @Column(name = "id", updatable = false, columnDefinition = "VARCHAR(36)")
    private String id;

    @Column(name = "user_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String userId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "goal", columnDefinition = "TEXT")
    private String goal;

    @Column(name = "daily_hours", columnDefinition = "SMALLINT")
    private Integer dailyHours;

    @Column(name = "is_ai_generated", columnDefinition = "TINYINT(1)")
    private Boolean isAiGenerated;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "cover_color", length = 20)
    private String coverColor;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void fillDefaults() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.dailyHours == null) {
            this.dailyHours = 2;
        }
        if (this.isAiGenerated == null) {
            this.isAiGenerated = false;
        }
        if (this.status == null) {
            this.status = "active";
        }
        if (this.coverColor == null) {
            this.coverColor = "#18a058";
        }
    }
}
