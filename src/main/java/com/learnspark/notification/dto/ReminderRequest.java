package com.learnspark.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

/**
 * 提醒设置更新请求体。
 *
 * <pre>
 * PUT /api/v1/user/reminder
 * { "email": "user@example.com", "reminderTime": "08:00:00", "timezone": "Asia/Shanghai", "enabled": true }
 * </pre>
 */
@Data
public class ReminderRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotNull(message = "提醒时间不能为空")
    private LocalTime reminderTime;

    private String timezone;

    private Boolean enabled;
}
