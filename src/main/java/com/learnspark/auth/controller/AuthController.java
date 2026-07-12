package com.learnspark.auth.controller;

import com.learnspark.auth.dto.AuthResponse;
import com.learnspark.auth.dto.LoginRequest;
import com.learnspark.auth.dto.RegisterRequest;
import com.learnspark.auth.service.AuthService;
import com.learnspark.common.result.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器：注册、登录。
 *
 * <p>路径前缀 /auth，配合 context-path /api/v1，完整路径为 /api/v1/auth/**。
 * 这些接口在 SecurityConfig 中配置为白名单（无需 token）。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册。
     *
     * <pre>
     * POST /api/v1/auth/register
     * { "email": "user@example.com", "password": "123456", "nickname": "灵犀" }
     * </pre>
     */
    @PostMapping("/register")
    public ApiResult<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResult.success(authService.register(request));
    }

    /**
     * 用户登录。
     *
     * <pre>
     * POST /api/v1/auth/login
     * { "email": "user@example.com", "password": "123456" }
     * </pre>
     */
    @PostMapping("/login")
    public ApiResult<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.success(authService.login(request));
    }
}
