package com.learnspark.gamification.controller;

import com.learnspark.common.result.ApiResult;
import com.learnspark.common.security.CurrentUser;
import com.learnspark.gamification.dto.DailyActivityDto;
import com.learnspark.gamification.dto.DashboardResponse;
import com.learnspark.gamification.dto.ProjectStatsResponse;
import com.learnspark.gamification.dto.UserStatsResponse;
import com.learnspark.gamification.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计控制器：项目统计 + 用户统计 + 仪表盘聚合。
 *
 * <p>端点：
 * <ul>
 *   <li>GET /projects/{id}/stats — 项目任务完成率、打卡、积分</li>
 *   <li>GET /user/stats — 用户全局统计（总积分、好评率等）</li>
 *   <li>GET /user/dashboard — 仪表盘聚合数据（今日任务+鼓励语）</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * 项目统计。
     * <p>完整路径：GET /api/v1/projects/{id}/stats
     */
    @GetMapping("/projects/{id}/stats")
    public ApiResult<ProjectStatsResponse> projectStats(@CurrentUser String userId,
                                                        @PathVariable("id") String projectId) {
        return ApiResult.success(statsService.getProjectStats(userId, projectId));
    }

    /**
     * 用户全局统计。
     * <p>完整路径：GET /api/v1/user/stats
     */
    @GetMapping("/user/stats")
    public ApiResult<UserStatsResponse> userStats(@CurrentUser String userId) {
        return ApiResult.success(statsService.getUserStats(userId));
    }

    /**
     * 仪表盘聚合数据（首页一次性加载）。
     * <p>完整路径：GET /api/v1/user/dashboard
     */
    @GetMapping("/user/dashboard")
    public ApiResult<DashboardResponse> dashboard(@CurrentUser String userId) {
        return ApiResult.success(statsService.getDashboard(userId));
    }

    /**
     * 用户每日活动统计（热力图数据）。
     * <p>完整路径：GET /api/v1/user/activity
     */
    @GetMapping("/user/activity")
    public ApiResult<List<DailyActivityDto>> userActivity(@CurrentUser String userId) {
        return ApiResult.success(statsService.getActivityHeatmap(userId));
    }
}
