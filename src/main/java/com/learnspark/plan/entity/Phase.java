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

import java.util.UUID;

/**
 * 学习阶段实体，对应 phases 表（1 项目 : N 阶段）。
 */
@Entity
@Table(name = "phases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Phase extends BaseEntity {

    @Id
    @Column(name = "id", updatable = false, columnDefinition = "VARCHAR(36)")
    private String id;

    @Column(name = "project_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String projectId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "objective", columnDefinition = "TEXT")
    private String objective;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @PrePersist
    void fillDefaults() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }
}
