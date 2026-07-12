package com.learnspark.plan.dto;

import com.learnspark.plan.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目响应体。
 */
@Data
@Builder
@AllArgsConstructor
public class ProjectResponse {

    private String id;
    private String name;
    private String description;
    private String goal;
    private Integer dailyHours;
    private Boolean isAiGenerated;
    private String status;
    private String coverColor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse from(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .goal(p.getGoal())
                .dailyHours(p.getDailyHours())
                .isAiGenerated(p.getIsAiGenerated())
                .status(p.getStatus())
                .coverColor(p.getCoverColor())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
