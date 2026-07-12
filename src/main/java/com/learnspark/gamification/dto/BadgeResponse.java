package com.learnspark.gamification.dto;

import com.learnspark.gamification.entity.Badge;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 徽章展示 DTO（含是否已解锁）。
 *
 * <p>前端据此渲染成就墙：已解锁正常显示，未解锁灰显。
 */
@Data
@Builder
@AllArgsConstructor
public class BadgeResponse {

    private String id;
    private String code;
    private String name;
    private String description;
    private String iconUrl;
    private String category;
    private String ruleType;
    private Integer ruleValue;

    /** 是否已解锁 */
    private boolean awarded;

    /** 解锁时间（未解锁为 null） */
    private LocalDateTime awardedAt;

    /**
     * 从徽章定义构造响应（默认未解锁）。
     */
    public static BadgeResponse from(Badge badge) {
        return BadgeResponse.builder()
                .id(badge.getId())
                .code(badge.getCode())
                .name(badge.getName())
                .description(badge.getDescription())
                .iconUrl(badge.getIconUrl())
                .category(badge.getCategory())
                .ruleType(badge.getRuleType())
                .ruleValue(badge.getRuleValue())
                .awarded(false)
                .build();
    }
}
