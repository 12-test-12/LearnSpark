-- 阶段 2.2：文件解析任务表（按文档 §八 V4）
-- 状态机: PENDING -> PROCESSING -> READY / FAILED

CREATE TABLE file_parse_jobs (
    id              VARCHAR(36) PRIMARY KEY,
    entry_id        VARCHAR(36) NOT NULL,
    file_path       TEXT NOT NULL,
    file_type       VARCHAR(20) NOT NULL,                -- pdf / docx / xlsx / pptx / png / jpg / md ...
    file_size       BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING / PROCESSING / READY / FAILED
    error_message   TEXT,
    retry_count     INT NOT NULL DEFAULT 0,
    worker_id       VARCHAR(100),                         -- 处理该任务的 worker 标识
    claimed_at      DATETIME(3),                          -- 抢占时间
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    completed_at    DATETIME(3),
    CONSTRAINT fk_parse_entry FOREIGN KEY (entry_id) REFERENCES knowledge_entries(id) ON DELETE CASCADE,
    INDEX idx_parse_status (status),
    INDEX idx_parse_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
