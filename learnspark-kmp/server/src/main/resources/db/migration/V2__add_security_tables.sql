-- 阶段 1.2：双令牌 + 设备绑定（按文档 §4.1.2）

CREATE TABLE devices (
    id                  VARCHAR(36) PRIMARY KEY,
    user_id             VARCHAR(36) NOT NULL,
    device_name         VARCHAR(100),
    device_type         VARCHAR(20) NOT NULL,    -- android / desktop / web
    device_fingerprint  VARCHAR(255),
    refresh_token_hash  VARCHAR(255),
    last_active_at      DATETIME(3),
    created_at          DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_devices_user (user_id),
    INDEX idx_devices_fingerprint (device_fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE revoked_tokens (
    id          VARCHAR(36) PRIMARY KEY,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    user_id     VARCHAR(36) NOT NULL,
    revoked_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_revoked_user (user_id),
    INDEX idx_revoked_at (revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
