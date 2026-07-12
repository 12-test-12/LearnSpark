package com.learnspark.gamification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户积分与打卡实体，对应 user_scores 表。
 *
 * <p>按 (user_id, project_id) 维度记录积分与连续打卡天数。
 * 连续打卡逻辑由 {@link com.learnspark.gamification.service.ScoreService} 维护：
 * lastCompletedDate 为昨天则 streak+1，为今天则不变，更早则归 1。
 */
@Entity
@Table(name = "user_scores")
@IdClass(UserScoreId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserScore {

    @Id
    @Column(name = "user_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String userId;

    @Id
    @Column(name = "project_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String projectId;

    @Column(name = "total_points")
    private Integer totalPoints;

    @Column(name = "streak_days")
    private Integer streakDays;

    @Column(name = "last_completed_date")
    private LocalDate lastCompletedDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void fillDefaults() {
        if (this.totalPoints == null) {
            this.totalPoints = 0;
        }
        if (this.streakDays == null) {
            this.streakDays = 0;
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void touchUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
