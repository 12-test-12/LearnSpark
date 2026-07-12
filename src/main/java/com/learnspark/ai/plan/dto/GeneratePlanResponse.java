package com.learnspark.ai.plan.dto;

import com.learnspark.plan.dto.PhaseResponse;
import com.learnspark.plan.dto.TaskResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI 生成学习路线响应体。
 *
 * <p>包含生成的阶段列表和扁平化的任务列表，供前端向导预览展示。
 */
@Data
@Builder
@AllArgsConstructor
public class GeneratePlanResponse {

    /** 项目 ID */
    private String projectId;

    /** 生成的阶段列表 */
    private List<PhaseResponse> phases;

    /** 生成的任务列表（扁平化，按阶段顺序排列） */
    private List<TaskResponse> tasks;

    /** 阶段数量 */
    private int phaseCount;

    /** 任务数量 */
    private int taskCount;
}
