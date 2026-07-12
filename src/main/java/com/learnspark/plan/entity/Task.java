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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 学习任务实体，对应 tasks 表（1 阶段 : N 任务）。
 *
 * <p>支持软删除（deleted_at），状态流转：pending → submitted → passed/failed。
 */
@Entity
@Table(name = "tasks")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task extends BaseEntity {

    @Id
    @Column(name = "id", updatable = false, columnDefinition = "VARCHAR(36)")
    private String id;

    @Column(name = "phase_id", columnDefinition = "VARCHAR(36)")
    private String phaseId;

    @Column(name = "project_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String projectId;

    @Column(name = "day_number")
    private Integer dayNumber;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "verification_criteria", columnDefinition = "TEXT")
    private String verificationCriteria;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void fillDefaults() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.status == null) {
            this.status = "pending";
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }
}
