package com.learnspark.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 阶段创建/更新请求体。
 */
@Data
public class PhaseRequest {

    @NotBlank(message = "projectId 不能为空")
    private String projectId;

    @NotBlank(message = "阶段名称不能为空")
    @Size(max = 255, message = "阶段名称最长 255 字符")
    private String name;

    private String objective;

    private Integer sortOrder;
}
