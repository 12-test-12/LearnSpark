package com.learnspark.gamification.controller;

import com.learnspark.common.result.ApiResult;
import com.learnspark.common.security.CurrentUser;
import com.learnspark.gamification.dto.BadgeResponse;
import com.learnspark.gamification.service.BadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成就系统控制器。
 *
 * <p>端点：
 * <ul>
 *   <li>GET /user/badges — 查询当前用户全部徽章（含已解锁/未解锁）</li>
 * </ul>
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class GamificationController {

    private final BadgeService badgeService;

    /**
     * 查询当前用户全部徽章。
     * <p>完整路径：GET /api/v1/user/badges
     */
    @GetMapping("/badges")
    public ApiResult<List<BadgeResponse>> getBadges(@CurrentUser String userId) {
        return ApiResult.success(badgeService.getUserBadges(userId));
    }
}
