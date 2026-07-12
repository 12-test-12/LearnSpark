package com.learnspark.plan.dto;

import com.learnspark.plan.entity.Task;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务响应体。
 */
@Data
@Builder
@AllArgsConstructor
public class TaskResponse {

    private String id;
    private String phaseId;
    private String projectId;
    private Integer dayNumber;
    private String title;
    private String description;
    private String verificationCriteria;
    private String status;
    private LocalDate dueDate;
    private LocalDateTime completedAt;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaskResponse from(Task t) {
        return TaskResponse.builder()
                .id(t.getId())
                .phaseId(t.getPhaseId())
                .projectId(t.getProjectId())
                .dayNumber(t.getDayNumber())
                .title(t.getTitle())
                .description(t.getDescription())
                .verificationCriteria(t.getVerificationCriteria())
                .status(t.getStatus())
                .dueDate(t.getDueDate())
                .completedAt(t.getCompletedAt())
                .sortOrder(t.getSortOrder())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
