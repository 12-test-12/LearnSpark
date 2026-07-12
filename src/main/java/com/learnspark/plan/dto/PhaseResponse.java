package com.learnspark.plan.dto;

import com.learnspark.plan.entity.Phase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阶段响应体。
 */
@Data
@Builder
@AllArgsConstructor
public class PhaseResponse {

    private String id;
    private String projectId;
    private String name;
    private String objective;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PhaseResponse from(Phase p) {
        return PhaseResponse.builder()
                .id(p.getId())
                .projectId(p.getProjectId())
                .name(p.getName())
                .objective(p.getObjective())
                .sortOrder(p.getSortOrder())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
