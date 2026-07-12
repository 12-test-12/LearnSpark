package com.learnspark.gamification.entity;

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

import java.time.LocalDateTime;

/**
 * 徽章定义实体，对应 badges 表。
 *
 * <p>初始数据由 V1__init.sql 插入 7 枚徽章。rule_type + rule_value 描述解锁条件：
 * <ul>
 *   <li>count + 1 → 首次通过审核（first_pass）</li>
 *   <li>streak + 7/30 → 连续打卡 N 天</li>
 *   <li>score + 100/500 → 累计积分达 N</li>
 *   <li>kb + 10 → 知识库条目达 N</li>
 *   <li>perfect + 5 → 连续 N 次满分</li>
 * </ul>
 */
@Entity
@Table(name = "badges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Badge {

    @Id
    @Column(name = "id", updatable = false, columnDefinition = "VARCHAR(36)")
    private String id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    @Column(name = "category", length = 50)
    private String category;

    /** 规则类型：count / streak / score / kb / perfect */
    @Column(name = "rule_type", length = 50)
    private String ruleType;

    /** 规则阈值，配合 rule_type 使用 */
    @Column(name = "rule_value")
    private Integer ruleValue;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void fillDefaults() {
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.category == null) {
            this.category = "general";
        }
    }
}
