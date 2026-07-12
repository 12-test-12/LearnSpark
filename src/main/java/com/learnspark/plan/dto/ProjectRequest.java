package com.learnspark.plan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 项目创建/更新请求体。
 */
@Data
public class ProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 255, message = "项目名称最长 255 字符")
    private String name;

    @Size(max = 65535, message = "描述过长")
    private String description;

    private String goal;

    @Min(value = 1, message = "每日学习时长至少 1 小时")
    @Max(value = 16, message = "每日学习时长最多 16 小时")
    private Integer dailyHours;

    private String coverColor;
}
