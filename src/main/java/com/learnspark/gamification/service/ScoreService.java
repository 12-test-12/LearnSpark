package com.learnspark.gamification.service;

import com.learnspark.gamification.entity.UserScore;
import com.learnspark.gamification.repository.UserScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 积分与连续打卡服务。
 *
 * <p>职责：任务通过审核后加分、维护连续打卡天数。
 * 连续打卡规则（以 lastCompletedDate 为锚点）：
 * <ul>
 *   <li>同一天多次通过 → streak 不变，仅累加积分（防重复打卡）</li>
 *   <li>上次是昨天 → streak + 1</li>
 *   <li>上次更早或为空 → streak 归 1（重新起算）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    private final UserScoreRepository scoreRepository;
    private final BadgeService badgeService;

    /**
     * 任务通过后加分并更新打卡。
     *
     * <p>由 {@link com.learnspark.submission.service.SubmissionService} 在任务通过后调用。
     * 幂等性由上游保证（SubmissionService 拒绝重复提交已通过任务）。
     *
     * @param userId    用户 ID
     * @param projectId 项目 ID
     * @param score     AI 评分（1-10）
     * @return 更新后的积分记录（供徽章检查使用）
     */
    @Transactional
    public UserScore awardOnPass(String userId, String projectId, int score) {
        UserScore userScore = getOrCreate(userId, projectId);
        userScore.setTotalPoints(userScore.getTotalPoints() + score);
        updateStreak(userScore, LocalDate.now());
        UserScore saved = scoreRepository.save(userScore);
        log.info("积分更新: userId={}, project={}, +{}分, 总分={}, streak={}",
                userId, projectId, score, saved.getTotalPoints(), saved.getStreakDays());
        // 加分后检查徽章解锁
        badgeService.checkAndAwardBadges(userId);
        return saved;
    }

    /** 获取或创建用户在某项目下的积分记录 */
    private UserScore getOrCreate(String userId, String projectId) {
        return scoreRepository.findByUserIdAndProjectId(userId, projectId)
                .orElseGet(() -> UserScore.builder()
                        .userId(userId)
                        .projectId(projectId)
                        .totalPoints(0)
                        .streakDays(0)
                        .build());
    }

    /**
     * 更新连续打卡天数。
     * 以 today 为基准对比 lastCompletedDate，决定 streak 走势。
     */
    private void updateStreak(UserScore score, LocalDate today) {
        LocalDate lastDate = score.getLastCompletedDate();
        if (lastDate == null) {
            score.setStreakDays(1);
        } else if (lastDate.equals(today)) {
            return; // 今日已打卡，streak 不变
        } else if (lastDate.equals(today.minusDays(1))) {
            score.setStreakDays(score.getStreakDays() + 1);
        } else {
            score.setStreakDays(1); // 断签，重新起算
        }
        score.setLastCompletedDate(today);
    }
}
