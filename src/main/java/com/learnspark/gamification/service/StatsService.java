package com.learnspark.gamification.service;

import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import com.learnspark.gamification.dto.DailyActivityDto;
import com.learnspark.gamification.dto.DashboardResponse;
import com.learnspark.gamification.dto.ProjectStatsResponse;
import com.learnspark.gamification.dto.UserStatsResponse;
import com.learnspark.gamification.entity.UserScore;
import com.learnspark.gamification.repository.UserScoreRepository;
import com.learnspark.knowledge.repository.KnowledgeEntryRepository;
import com.learnspark.plan.dto.TaskResponse;
import com.learnspark.plan.repository.ProjectRepository;
import com.learnspark.plan.repository.TaskRepository;
import com.learnspark.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计服务：项目统计 + 用户全局统计 + 仪表盘聚合。
 *
 * <p>聚合跨模块数据（任务/提交/积分/知识库），为前端 Dashboard 和统计页提供一次性数据。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final UserScoreRepository scoreRepository;
    private final KnowledgeEntryRepository knowledgeEntryRepository;
    private final DailyQuoteService dailyQuoteService;

    /** 仪表盘今日任务展示上限 */
    private static final int DASHBOARD_TASK_LIMIT = 5;

    /** 热力图回溯天数 */
    private static final int HEATMAP_DAYS = 90;

    /**
     * 项目统计：任务总数、完成率、打卡、积分。
     */
    @Transactional(readOnly = true)
    public ProjectStatsResponse getProjectStats(String userId, String projectId) {
        verifyProjectOwnership(userId, projectId);
        long total = taskRepository.countByProjectId(projectId);
        long passed = taskRepository.countByProjectIdAndStatus(projectId, "passed");
        double rate = calcRate(passed, total);
        UserScore score = scoreRepository.findByUserIdAndProjectId(userId, projectId)
                .orElse(null);
        return ProjectStatsResponse.builder()
                .totalTasks(total)
                .passedTasks(passed)
                .completionRate(rate)
                .streakDays(score != null ? score.getStreakDays() : 0)
                .totalPoints(score != null ? score.getTotalPoints() : 0)
                .build();
    }

    /**
     * 用户全局统计：总积分、打卡、知识库、提交、好评率。
     */
    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats(String userId) {
        long totalSubs = submissionRepository.countByUserId(userId);
        long passedSubs = submissionRepository.countByUserIdAndPassedTrue(userId);
        return UserStatsResponse.builder()
                .totalPoints(scoreRepository.sumTotalPointsByUserId(userId))
                .maxStreakDays(scoreRepository.maxStreakDaysByUserId(userId))
                .knowledgeCount(knowledgeEntryRepository.countByUserId(userId))
                .totalSubmissions(totalSubs)
                .passedSubmissions(passedSubs)
                .approvalRate(calcRate(passedSubs, totalSubs))
                .build();
    }

    /**
     * 仪表盘聚合数据：今日任务 + 打卡 + 积分 + 知识库 + 每日一句。
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String userId) {
        List<TaskResponse> todayTasks = loadTodayTasks(userId);
        return DashboardResponse.builder()
                .todayPendingCount(todayTasks.size())
                .todayTasks(todayTasks)
                .maxStreakDays(scoreRepository.maxStreakDaysByUserId(userId))
                .totalPoints(scoreRepository.sumTotalPointsByUserId(userId))
                .knowledgeCount(knowledgeEntryRepository.countByUserId(userId))
                .dailyQuote(dailyQuoteService.getTodayQuote())
                .build();
    }

    /**
     * 获取用户近 90 天每日提交活动统计（用于仪表盘热力图）。
     *
     * <p>加载时间窗口内全部提交后按天分组计数，避免 DB 端 DATE() 函数的类型差异。
     */
    @Transactional(readOnly = true)
    public List<DailyActivityDto> getActivityHeatmap(String userId) {
        LocalDateTime since = LocalDate.now().minusDays(HEATMAP_DAYS).atStartOfDay();
        Map<LocalDate, Long> dailyCounts = submissionRepository
                .findByUserIdAndSubmittedAtAfter(userId, since)
                .stream()
                .filter(s -> s.getSubmittedAt() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getSubmittedAt().toLocalDate(),
                        Collectors.counting()));
        return dailyCounts.entrySet().stream()
                .map(e -> new DailyActivityDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(DailyActivityDto::date))
                .toList();
    }

    /** 加载今日待完成任务（最多 DASHBOARD_TASK_LIMIT 条） */
    private List<TaskResponse> loadTodayTasks(String userId) {
        return taskRepository.findTodayPendingTasksByUserId(userId, LocalDate.now())
                .stream()
                .limit(DASHBOARD_TASK_LIMIT)
                .map(TaskResponse::from)
                .toList();
    }

    /** 计算比率（0-100，保留 1 位小数），分母为 0 时返回 0 */
    private double calcRate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    /** 校验项目归属（不存在或无权访问时抛 PROJECT_NOT_FOUND） */
    private void verifyProjectOwnership(String userId, String projectId) {
        if (!projectRepository.existsByIdAndUserId(projectId, userId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
