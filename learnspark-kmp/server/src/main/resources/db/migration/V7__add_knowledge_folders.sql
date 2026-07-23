-- =============================================================
-- V7: 知识库文件夹（多层树）+ knowledge_entries 加 folder_id
--
-- 设计要点：
-- - knowledge_folders.parent_id 可空（null = 根目录）
-- - path 字段冗余存储完整路径（"/编程/Java/多线程"），加快树查询
-- - 唯一约束：(user_id, parent_id, name) - 同级目录下不能重名
-- - knowledge_entries.folder_id ON DELETE SET NULL（删除文件夹不删条目，条目回到根）
-- =============================================================

CREATE TABLE knowledge_folders (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    parent_id       VARCHAR(36) NULL,                          -- null = 根目录
    name            VARCHAR(100) NOT NULL,
    color           VARCHAR(20),                               -- "#FF6B6B" 或 null（用默认）
    icon            VARCHAR(50),                               -- "📁" / "📚" 等 emoji 或 icon key
    sort_order      INT NOT NULL DEFAULT 0,
    path            VARCHAR(1000) NOT NULL,                    -- 冗余："/编程/Java/多线程"
    depth           INT NOT NULL DEFAULT 0,                    -- 冗余：根=0，子=1，孙=2...
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_kf_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_kf_parent FOREIGN KEY (parent_id) REFERENCES knowledge_folders(id) ON DELETE CASCADE,
    UNIQUE KEY uq_kf_user_parent_name (user_id, parent_id, name),
    INDEX idx_kf_user (user_id),
    INDEX idx_kf_parent (parent_id),
    INDEX idx_kf_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- knowledge_entries 加 folder_id 字段（NULL = 根目录）
ALTER TABLE knowledge_entries
    ADD COLUMN folder_id VARCHAR(36) NULL AFTER user_id,
    ADD CONSTRAINT fk_ke_folder FOREIGN KEY (folder_id) REFERENCES knowledge_folders(id) ON DELETE SET NULL,
    ADD INDEX idx_ke_folder (folder_id),
    ADD INDEX idx_ke_user_folder (user_id, folder_id);
