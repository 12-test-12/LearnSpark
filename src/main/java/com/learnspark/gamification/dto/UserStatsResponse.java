package com.learnspark.gamification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 用户全局统计响应（GET /api/v1/user/stats）。
 */
@Data
@Builder
@AllArgsConstructor
public class UserStatsResponse {

    /** 跨项目总积分 */
    private int totalPoints;

    /** 最大连续打卡天数 */
    private int maxStreakDays;

    /** 知识库条目数 */
    private long knowledgeCount;

    /** 总提交数 */
    private long totalSubmissions;

    /** 通过审核的提交数 */
    private long passedSubmissions;

    /** AI 好评率（0-100，通过率） */
    private double approvalRate;
}
