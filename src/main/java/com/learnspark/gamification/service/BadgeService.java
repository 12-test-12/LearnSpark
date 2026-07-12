package com.learnspark.gamification.service;

import com.learnspark.gamification.dto.BadgeResponse;
import com.learnspark.gamification.entity.Badge;
import com.learnspark.gamification.entity.UserBadge;
import com.learnspark.gamification.repository.BadgeRepository;
import com.learnspark.gamification.repository.UserBadgeRepository;
import com.learnspark.gamification.repository.UserScoreRepository;
import com.learnspark.knowledge.repository.KnowledgeEntryRepository;
import com.learnspark.submission.entity.Submission;
import com.learnspark.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 徽章服务：规则检查 + 发放 + 查询。
 *
 * <p>徽章规则由 badges 表的 rule_type + rule_value 定义：
 * <ul>
 *   <li>count + N → 通过审核的提交数 ≥ N（如 first_pass）</li>
 *   <li>streak + N → 最大连续打卡天数 ≥ N</li>
 *   <li>score + N → 跨项目总积分 ≥ N</li>
 *   <li>kb + N → 知识库条目数 ≥ N</li>
 *   <li>perfect + N → 最近 N 次提交均为满分（ai_score=10）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserScoreRepository scoreRepository;
    private final SubmissionRepository submissionRepository;
    private final KnowledgeEntryRepository knowledgeEntryRepository;

    /** 完美评分满分阈值 */
    private static final int PERFECT_SCORE = 10;

    /**
     * 检查并发放徽章（任务通过后由 ScoreService 调用）。
     *
     * @return 本次新解锁的徽章列表（供前端触发撒花动画）
     */
    @Transactional
    public List<Badge> checkAndAwardBadges(String userId) {
        List<Badge> allBadges = badgeRepository.findAllByOrderByCategoryAscRuleValueAsc();
        List<Badge> newlyAwarded = new ArrayList<>();
        for (Badge badge : allBadges) {
            if (isAlreadyAwarded(userId, badge.getId())) {
                continue;
            }
            if (meetsRule(badge, userId)) {
                awardBadge(userId, badge);
                newlyAwarded.add(badge);
                log.info("徽章解锁: userId={}, badge={}", userId, badge.getCode());
            }
        }
        return newlyAwarded;
    }

    /**
     * 查询用户全部徽章（含已解锁/未解锁状态）。
     */
    @Transactional(readOnly = true)
    public List<BadgeResponse> getUserBadges(String userId) {
        List<Badge> allBadges = badgeRepository.findAllByOrderByCategoryAscRuleValueAsc();
        Map<String, UserBadge> awardedMap = loadAwardedMap(userId);
        return allBadges.stream()
                .map(badge -> toResponse(badge, awardedMap.get(badge.getId())))
                .collect(Collectors.toList());
    }

    // ==================== 规则检查 ====================

    /** 判断用户是否已获得某徽章 */
    private boolean isAlreadyAwarded(String userId, String badgeId) {
        return userBadgeRepository.existsByUserIdAndBadgeId(userId, badgeId);
    }

    /** 分发到具体规则检查器 */
    private boolean meetsRule(Badge badge, String userId) {
        String ruleType = badge.getRuleType();
        int ruleValue = badge.getRuleValue() != null ? badge.getRuleValue() : 0;
        return switch (ruleType) {
            case "count" -> countByUserIdAndPassedTrue(userId) >= ruleValue;
            case "streak" -> scoreRepository.maxStreakDaysByUserId(userId) >= ruleValue;
            case "score" -> scoreRepository.sumTotalPointsByUserId(userId) >= ruleValue;
            case "kb" -> knowledgeEntryRepository.countByUserId(userId) >= ruleValue;
            case "perfect" -> checkPerfectStreak(userId, ruleValue);
            default -> false;
        };
    }

    /** 检查最近 N 次提交是否全部满分 */
    private boolean checkPerfectStreak(String userId, int requiredCount) {
        List<Submission> recent = submissionRepository
                .findTop5ByUserIdOrderBySubmittedAtDesc(userId);
        if (recent.size() < requiredCount) {
            return false;
        }
        return recent.stream()
                .limit(requiredCount)
                .allMatch(s -> s.getAiScore() != null && s.getAiScore() == PERFECT_SCORE);
    }

    /** 通过审核的提交数（包装以便 switch 引用） */
    private long countByUserIdAndPassedTrue(String userId) {
        return submissionRepository.countByUserIdAndPassedTrue(userId);
    }

    // ==================== 发放与组装 ====================

    /** 发放徽章（创建 user_badges 记录） */
    private void awardBadge(String userId, Badge badge) {
        UserBadge userBadge = UserBadge.builder()
                .userId(userId)
                .badgeId(badge.getId())
                .build();
        userBadgeRepository.save(userBadge);
    }

    /** 加载用户已获徽章 Map（badgeId → UserBadge），避免逐条查询 */
    private Map<String, UserBadge> loadAwardedMap(String userId) {
        return userBadgeRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserBadge::getBadgeId, Function.identity()));
    }

    /** 组装单个 BadgeResponse（标记是否已解锁） */
    private BadgeResponse toResponse(Badge badge, UserBadge userBadge) {
        BadgeResponse response = BadgeResponse.from(badge);
        if (userBadge != null) {
            response.setAwarded(true);
            response.setAwardedAt(userBadge.getAwardedAt());
        }
        return response;
    }
}
