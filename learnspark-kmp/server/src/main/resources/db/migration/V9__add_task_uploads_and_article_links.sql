-- =============================================================
-- V9: 任务上传 + AI 标注可参考文章
-- =============================================================

-- 1) 任务上传的文件元数据
CREATE TABLE task_uploads (
    id              VARCHAR(36) PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    knowledge_entry_id VARCHAR(36),  -- 解析后落到知识库的 entry（可空，解析失败时为空）
    folder_id       VARCHAR(36),     -- 用户上传时选定的知识库文件夹
    file_name       VARCHAR(255) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_type       VARCHAR(50)  NOT NULL,
    file_size       BIGINT       NOT NULL DEFAULT 0,
    upload_status   VARCHAR(20)  NOT NULL DEFAULT 'pending',  -- pending/parsing/ready/failed
    parse_error     TEXT,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_task_uploads_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_uploads_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_task_uploads_task (task_id),
    INDEX idx_task_uploads_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) AI 标注的可参考文章
CREATE TABLE task_article_links (
    id              VARCHAR(36) PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    entry_id        VARCHAR(36) NOT NULL,  -- knowledge_entries.id
    reason          VARCHAR(500) NOT NULL,  -- AI 解释：为什么这篇文章能解决这个任务
    relevance       TINYINT     NOT NULL DEFAULT 50,  -- 0-100 评分
    source          VARCHAR(20) NOT NULL DEFAULT 'ai',  -- ai / manual
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_task_links_task  FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_links_entry FOREIGN KEY (entry_id) REFERENCES knowledge_entries(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_links_user  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_task_entry (task_id, entry_id),
    INDEX idx_task_links_task (task_id),
    INDEX idx_task_links_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
