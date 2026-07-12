package com.learnspark.gamification.dto;

import com.learnspark.plan.dto.TaskResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 仪表盘聚合数据（GET /api/v1/user/dashboard）。
 *
 * <p>一次请求返回首页所需的全部数据，避免前端多次往返。
 */
@Data
@Builder
@AllArgsConstructor
public class DashboardResponse {

    /** 今日待完成任务数 */
    private int todayPendingCount;

    /** 今日待完成任务列表（最多 5 条） */
    private List<TaskResponse> todayTasks;

    /** 最大连续打卡天数 */
    private int maxStreakDays;

    /** 跨项目总积分 */
    private int totalPoints;

    /** 知识库条目数 */
    private long knowledgeCount;

    /** AI 每日一句（鼓励语） */
    private String dailyQuote;
}
