package com.learnspark.gamification.entity;

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
 * 用户已获徽章实体，对应 user_badges 表。
 *
 * <p>复合主键 (user_id, badge_id)。一条记录代表用户已解锁某徽章。
 */
@Entity
@Table(name = "user_badges")
@IdClass(UserBadgeId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBadge {

    @Id
    @Column(name = "user_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String userId;

    @Id
    @Column(name = "badge_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String badgeId;

    @Column(name = "awarded_at", updatable = false)
    private LocalDateTime awardedAt;

    @PrePersist
    void fillDefaults() {
        if (this.awardedAt == null) {
            this.awardedAt = LocalDateTime.now();
        }
    }
}
