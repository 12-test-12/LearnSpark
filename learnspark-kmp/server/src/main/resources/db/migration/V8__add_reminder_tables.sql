-- =============================================================
-- V8: Reminder 调度器（按用户自定义时间触发站内通知）
--
-- 设计要点：
-- - reminder_settings：用户配置的提醒规则
--   - type: task_due / check_in / custom
--   - trigger_time: HH:mm:ss（每日触发时刻）
--   - repeat_pattern: once / daily / weekdays / weekly
--   - weekday_mask: 位掩码 bit0=Mon..bit6=Sun（仅 weekly 模式生效）
-- - reminder_logs：每次触发的实际记录（客户端 GET /pending 轮询拉取）
--   - acknowledged: 0/1（确认状态）
-- =============================================================

CREATE TABLE reminder_settings (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    type            VARCHAR(30) NOT NULL,                       -- task_due / check_in / custom
    title           VARCHAR(200) NOT NULL,
    message         TEXT,
    target_id       VARCHAR(36),                                -- 关联的 task / project id（可空）
    trigger_time    TIME NOT NULL,                              -- HH:mm:ss
    repeat_pattern  VARCHAR(20) NOT NULL DEFAULT 'daily',       -- once / daily / weekdays / weekly
    weekday_mask    INT NOT NULL DEFAULT 127,                   -- bit0=Mon..bit6=Sun；127=全选
    next_fire_at    DATETIME(3),                                -- 下次触发时间（reminder_scheduler 维护）
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_rs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_rs_user_enabled (user_id, enabled),
    INDEX idx_rs_next_fire (next_fire_at, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reminder_logs (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    setting_id      VARCHAR(36) NOT NULL,
    fired_at        DATETIME(3) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    message         TEXT,
    target_id       VARCHAR(36),
    acknowledged    TINYINT(1) NOT NULL DEFAULT 0,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_rl_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_rl_setting FOREIGN KEY (setting_id) REFERENCES reminder_settings(id) ON DELETE CASCADE,
    INDEX idx_rl_user_ack (user_id, acknowledged, fired_at),
    INDEX idx_rl_user_fired (user_id, fired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
