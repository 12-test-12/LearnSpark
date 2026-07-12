package com.learnspark.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 任务创建/更新请求体。
 */
@Data
public class TaskRequest {

    @NotBlank(message = "projectId 不能为空")
    private String projectId;

    /** 可选，关联阶段 */
    private String phaseId;

    private Integer dayNumber;

    @Size(max = 500, message = "标题最长 500 字符")
    private String title;

    @NotBlank(message = "任务描述不能为空")
    private String description;

    private String verificationCriteria;

    private LocalDate dueDate;

    private Integer sortOrder;
}
