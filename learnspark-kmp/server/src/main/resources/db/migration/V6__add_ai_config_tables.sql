-- =============================================================
-- V6: AI 配置表（按文档 §11.2.4：user_ai_configs）
--
-- 设计要点：
-- - api_key 加密后存于 api_key_cipher 字段（AES-256-GCM）
-- - base64 编码的 iv 前缀存储（每次加密随机 iv）
-- - unique(user_id, provider) 约束：每用户每 provider 最多一条配置
-- - enabled 字段：用户可临时停用，无需删除
-- =============================================================

CREATE TABLE user_ai_configs (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    provider        VARCHAR(50) NOT NULL,                       -- deepseek / openai / qwen ...
    api_key_cipher  TEXT NOT NULL,                              -- AES-256-GCM(base64(iv) + ciphertext)
    model           VARCHAR(100) NOT NULL,                      -- deepseek-chat / gpt-4 ...
    base_url        VARCHAR(255),                               -- 自定义端点（可选）
    max_tokens      INT NOT NULL DEFAULT 2048,
    temperature     DECIMAL(3,2) NOT NULL DEFAULT 0.70,         -- 0.00 ~ 2.00
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_ai_config_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_provider (user_id, provider),
    INDEX idx_ai_user (user_id),
    INDEX idx_ai_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V5 已有 submissions 表，但缺 updated_at 索引（拉取用）
-- 这里只是补索引，不动业务字段
CREATE INDEX idx_submissions_task ON submissions(task_id);
CREATE INDEX idx_submissions_user_updated ON submissions(user_id, updated_at);
