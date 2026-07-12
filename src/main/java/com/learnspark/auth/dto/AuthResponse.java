package com.learnspark.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 认证响应体（注册 / 登录成功后返回）。
 *
 * <pre>
 * { "token": "eyJhbGci...", "userInfo": { "id": "...", "email": "...", ... } }
 * </pre>
 */
@Data
@AllArgsConstructor
public class AuthResponse {

    /** JWT 令牌，前端存入 localStorage 并在后续请求的 Authorization 头中携带 */
    private String token;

    /** 用户信息（脱敏） */
    private UserInfo userInfo;
}
