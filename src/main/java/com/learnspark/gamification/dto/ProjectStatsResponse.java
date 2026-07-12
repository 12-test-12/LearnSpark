package com.learnspark.gamification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 项目统计响应（GET /api/v1/projects/{id}/stats）。
 */
@Data
@Builder
@AllArgsConstructor
public class ProjectStatsResponse {

    /** 项目下任务总数 */
    private long totalTasks;

    /** 已通过任务数 */
    private long passedTasks;

    /** 完成率（0-100，保留 1 位小数） */
    private double completionRate;

    /** 当前连续打卡天数 */
    private int streakDays;

    /** 项目累计积分 */
    private int totalPoints;
}
