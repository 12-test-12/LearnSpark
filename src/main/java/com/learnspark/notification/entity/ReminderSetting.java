package com.learnspark.notification.entity;

import com.learnspark.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 提醒设置实体，对应 reminder_settings 表。
 *
 * <p>1:1 关联 users 表，主键即 user_id。
 * 每用户一条记录，记录提醒时间、时区、开关与上次发送时间。
 */
@Entity
@Table(name = "reminder_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderSetting extends BaseEntity {

    /** 用户 ID（主键 + 外键 → users.id） */
    @Id
    @Column(name = "user_id", columnDefinition = "VARCHAR(36)")
    private String userId;

    /** 接收提醒的邮箱 */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** 每日提醒时间（如 08:00:00） */
    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime;

    /** 时区（如 Asia/Shanghai） */
    @Column(name = "timezone", length = 50)
    private String timezone;

    /** 是否启用提醒 */
    @Column(name = "enabled")
    private Boolean enabled;

    /** 通知渠道（JSON，预留扩展，如 ["email","browser"]） */
    @Column(name = "channels", columnDefinition = "JSON")
    private String channels;

    /** 上次发送时间（防重复发送） */
    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    /** 持久化前填充默认值 */
    @PrePersist
    void fillDefaults() {
        if (this.timezone == null) {
            this.timezone = "Asia/Shanghai";
        }
        if (this.enabled == null) {
            this.enabled = true;
        }
    }
}
