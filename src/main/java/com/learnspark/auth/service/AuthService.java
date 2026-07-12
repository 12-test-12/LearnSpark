package com.learnspark.auth.service;

import com.learnspark.auth.dto.AuthResponse;
import com.learnspark.auth.dto.LoginRequest;
import com.learnspark.auth.dto.RegisterRequest;
import com.learnspark.auth.dto.UserInfo;
import com.learnspark.auth.entity.User;
import com.learnspark.auth.repository.UserRepository;
import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import com.learnspark.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 认证服务：注册、登录。
 *
 * <p>注册流程：邮箱查重 → BCrypt 哈希密码 → 持久化 → 签发 JWT。
 * 登录流程：按邮箱查用户 → 校验密码 → 校验状态 → 更新最后登录时间 → 签发 JWT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * 用户注册。
     *
     * @param request 注册请求（email + password + 可选 nickname）
     * @return 认证响应（token + 用户信息）
     * @throws BusinessException 邮箱已注册
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getEmail())
                .build();
        user = userRepository.save(user);
        log.info("用户注册成功: id={}, email={}", user.getId(), user.getEmail());

        return buildAuthResponse(user);
    }

    /**
     * 用户登录。
     *
     * @param request 登录请求（email + password）
     * @return 认证响应（token + 用户信息）
     * @throws BusinessException 邮箱或密码错误 / 账号已禁用
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_OR_PASSWORD_WRONG));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.EMAIL_OR_PASSWORD_WRONG);
        }
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("用户登录成功: id={}, email={}", user.getId(), user.getEmail());

        return buildAuthResponse(user);
    }

    /** 组装认证响应：签发 token + 脱敏用户信息 */
    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generate(user.getId());
        UserInfo userInfo = UserInfo.from(user);
        return new AuthResponse(token, userInfo);
    }
}
