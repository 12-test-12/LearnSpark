package com.learnspark.auth.controller;

import com.learnspark.auth.dto.UserInfo;
import com.learnspark.auth.entity.User;
import com.learnspark.auth.repository.UserRepository;
import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import com.learnspark.common.result.ApiResult;
import com.learnspark.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器：获取当前用户信息。
 *
 * <p>路径前缀 /user，完整路径 /api/v1/user/**，需携带有效 token。
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * 获取当前登录用户信息。
     *
     * <pre>
     * GET /api/v1/user/me
     * Authorization: Bearer &lt;token&gt;
     * </pre>
     */
    @GetMapping("/me")
    public ApiResult<UserInfo> me(@CurrentUser String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        return ApiResult.success(UserInfo.from(user));
    }
}
