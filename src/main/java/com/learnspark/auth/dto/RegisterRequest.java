package com.learnspark.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求体。
 *
 * <pre>
 * POST /api/v1/auth/register
 * { "email": "user@example.com", "password": "123456", "nickname": "灵犀" }
 * </pre>
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度需在 6~50 位之间")
    private String password;

    @Size(max = 100, message = "昵称最长 100 字符")
    private String nickname;
}
