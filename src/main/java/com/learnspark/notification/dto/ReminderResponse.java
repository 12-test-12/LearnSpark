package com.learnspark.notification.dto;

import com.learnspark.notification.entity.ReminderSetting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 提醒设置响应体。
 */
@Data
@Builder
@AllArgsConstructor
public class ReminderResponse {

    private String email;
    private LocalTime reminderTime;
    private String timezone;
    private Boolean enabled;
    private LocalDateTime lastSentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReminderResponse from(ReminderSetting r) {
        if (r == null) {
            // 未配置时返回默认值
            return ReminderResponse.builder()
                    .email("")
                    .reminderTime(null)
                    .timezone("Asia/Shanghai")
                    .enabled(false)
                    .lastSentAt(null)
                    .createdAt(null)
                    .updatedAt(null)
                    .build();
        }
        return ReminderResponse.builder()
                .email(r.getEmail())
                .reminderTime(r.getReminderTime())
                .timezone(r.getTimezone())
                .enabled(r.getEnabled())
                .lastSentAt(r.getLastSentAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
