-- LearnSpark 基础 schema（阶段二起步）
-- 按文档 §八 数据库变更 设计

CREATE TABLE users (
    id              VARCHAR(36) PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    username        VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    avatar_url      TEXT,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_entries (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    content         MEDIUMTEXT,
    source_type     VARCHAR(20) NOT NULL DEFAULT 'manual',  -- manual / file / link
    source_path     TEXT,                                   -- 文件路径或 URL
    file_size       BIGINT,
    file_type       VARCHAR(50),                            -- pdf / docx / png ...
    parse_status    VARCHAR(20) DEFAULT 'ready',            -- ready / pending / processing / failed
    parse_error     TEXT,
    tags            VARCHAR(500),                           -- 逗号分隔的标签
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_knowledge_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_knowledge_user (user_id),
    INDEX idx_knowledge_status (parse_status),
    INDEX idx_knowledge_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库全文检索（MySQL FULLTEXT + ngram）
ALTER TABLE knowledge_entries
    ADD FULLTEXT INDEX ft_knowledge_title_content (title, content) WITH PARSER ngram;

-- 阶段一已有 users 初始化默认账号（仅 dev 环境）
INSERT INTO users (id, email, username, password_hash)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'dev@learnspark.local',
    'dev',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'  -- password: "password"
) ON DUPLICATE KEY UPDATE username = username;
