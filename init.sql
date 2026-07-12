-- =============================================================================
-- LearnSpark 数据库初始化脚本 (MySQL 版)
-- 要求：MySQL 8.0.16+（CHECK 约束生效）+ InnoDB + utf8mb4 + ngram 分词器
-- 执行前先建库：
--   CREATE DATABASE learnspark DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;
--   USE learnspark;
-- 然后执行：mysql -u root -p learnspark < init.sql
--
-- 与 PostgreSQL 版的主要差异：
--   1) 主键 UUID：用 CHAR(36) DEFAULT (UUID())，应用层用 UUID 字符串
--   2) 时间类型：TIMESTAMPTZ → DATETIME(3)，应用层统一 UTC
--   3) 数组 TEXT[] → JSON
--   4) JSONB → JSON
--   5) 向量 vector(1536) → JSON（存 float 数组）；大规模语义检索建议接入
--      外部向量库（Chroma / Milvus / Qdrant），MySQL 9.0+ 才有原生 VECTOR
--   6) 全文检索：tsvector+GIN → FULLTEXT INDEX + ngram 分词器（支持中文）
--   7) 触发器：PG 的可复用函数 → MySQL 每表单独触发器
--   8) HNSW 向量索引、pg_trgm 模糊索引：移除（MySQL 无对应实现）
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- =============================================================================
-- 1. 用户与权限模块
-- =============================================================================

CREATE TABLE users (
  id              CHAR(36)     NOT NULL DEFAULT (UUID()),
  email           VARCHAR(255) NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,
  nickname        VARCHAR(100) DEFAULT NULL,
  avatar_url      TEXT         DEFAULT NULL,
  timezone        VARCHAR(50)  DEFAULT 'Asia/Shanghai',
  status          VARCHAR(20)  DEFAULT 'active',
  last_login_at   DATETIME(3)  DEFAULT NULL,
  created_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  CONSTRAINT chk_users_status CHECK (status IN ('active','disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户表';

CREATE INDEX idx_users_email_lower ON users ((LOWER(email)));


CREATE TABLE user_ai_config (
  user_id                    CHAR(36)     NOT NULL,
  deepseek_api_key_encrypted TEXT,
  deepseek_base_url          VARCHAR(255) DEFAULT 'https://api.deepseek.com/v1',
  deepseek_model             VARCHAR(100) DEFAULT 'deepseek-chat',
  search_api_key_encrypted   TEXT,
  search_provider            VARCHAR(50)  DEFAULT 'bing',
  local_mode                 TINYINT(1)   DEFAULT 0,
  embedding_model            VARCHAR(100) DEFAULT 'bge-large-zh',
  created_at                 DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  updated_at                 DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id),
  CONSTRAINT fk_ai_config_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='AI 配置表（1 用户 : 1）';


-- =============================================================================
-- 2. 学习计划模块
-- =============================================================================

CREATE TABLE projects (
  id              CHAR(36)     NOT NULL DEFAULT (UUID()),
  user_id         CHAR(36)     NOT NULL,
  name            VARCHAR(255) NOT NULL,
  description     TEXT,
  goal            TEXT,
  daily_hours     SMALLINT     DEFAULT 2,
  is_ai_generated TINYINT(1)   DEFAULT 0,
  status          VARCHAR(20)  DEFAULT 'active',
  cover_color     VARCHAR(20)  DEFAULT '#18a058',
  deleted_at      DATETIME(3)  DEFAULT NULL,
  created_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_projects_user_id (user_id),
  KEY idx_projects_status (status),
  CONSTRAINT fk_projects_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT chk_projects_status CHECK (status IN ('active','archived','completed')),
  CONSTRAINT chk_projects_hours CHECK (daily_hours BETWEEN 1 AND 16)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='学习项目表';


CREATE TABLE phases (
  id          CHAR(36)     NOT NULL DEFAULT (UUID()),
  project_id  CHAR(36)     NOT NULL,
  name        VARCHAR(255) NOT NULL,
  objective   TEXT,
  sort_order  INT          NOT NULL DEFAULT 0,
  created_at  DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_phases_project_order (project_id, sort_order),
  CONSTRAINT fk_phases_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='学习阶段表（1 项目 : N）';


CREATE TABLE tasks (
  id                    CHAR(36)     NOT NULL DEFAULT (UUID()),
  phase_id              CHAR(36)     DEFAULT NULL,
  project_id            CHAR(36)     NOT NULL,
  day_number            INT          DEFAULT NULL,
  title                 VARCHAR(500) DEFAULT NULL,
  description           TEXT         NOT NULL,
  verification_criteria TEXT,
  status                VARCHAR(20)  DEFAULT 'pending',
  due_date              DATE         DEFAULT NULL,
  completed_at          DATETIME(3)  DEFAULT NULL,
  sort_order            INT          DEFAULT 0,
  deleted_at            DATETIME(3)  DEFAULT NULL,
  created_at            DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tasks_project_status (project_id, status),
  KEY idx_tasks_due_date (due_date),
  KEY idx_tasks_phase_order (phase_id, sort_order),
  CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
  CONSTRAINT fk_tasks_phase FOREIGN KEY (phase_id) REFERENCES phases(id) ON DELETE SET NULL,
  CONSTRAINT chk_tasks_status CHECK (status IN ('pending','submitted','passed','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='任务表（1 阶段 : N）';


-- =============================================================================
-- 3. 提交与审核模块
-- =============================================================================

CREATE TABLE submissions (
  id              CHAR(36)     NOT NULL DEFAULT (UUID()),
  task_id         CHAR(36)     NOT NULL,
  user_id         CHAR(36)     NOT NULL,
  content         TEXT         NOT NULL,
  attachment_urls JSON         DEFAULT NULL,                -- 附件链接数组
  ai_feedback     TEXT,
  ai_score        SMALLINT     DEFAULT NULL,
  passed          TINYINT(1)   DEFAULT NULL,
  ai_model        VARCHAR(100) DEFAULT NULL,
  ai_raw_response JSON         DEFAULT NULL,
  submitted_at    DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  reviewed_at     DATETIME(3)  DEFAULT NULL,
  created_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_submissions_task (task_id, submitted_at),
  KEY idx_submissions_user_time (user_id, submitted_at),
  CONSTRAINT fk_sub_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
  CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT chk_sub_score CHECK (ai_score BETWEEN 1 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='任务提交记录表';


-- =============================================================================
-- 4. 知识库模块
-- =============================================================================

CREATE TABLE knowledge_entries (
  id                CHAR(36)     NOT NULL DEFAULT (UUID()),
  user_id           CHAR(36)     NOT NULL,
  project_id        CHAR(36)     DEFAULT NULL,
  title             VARCHAR(500) NOT NULL,
  content           MEDIUMTEXT,
  content_md        MEDIUMTEXT,
  summary           TEXT,
  source_type       VARCHAR(50)  DEFAULT NULL,
  source_id         CHAR(36)     DEFAULT NULL,
  file_path         TEXT,
  mime_type         VARCHAR(100) DEFAULT NULL,
  tags              JSON         DEFAULT NULL,              -- 字符串数组，如 ["React","Hooks"]
  word_count        INT          DEFAULT 0,
  parse_status      VARCHAR(20)  DEFAULT 'done',
  -- 向量字段：JSON 存 1536 维 float 数组。
  -- 注意：MySQL 8.0 无原生向量索引，大规模语义检索建议接入外部向量库。
  -- MySQL 9.0+ 可改用 VECTOR(1536) 类型 + VECTOR_INDEX。
  vector_embedding  JSON         DEFAULT NULL COMMENT '1536维float数组,语义检索用,建议外部向量库',
  deleted_at        DATETIME(3)  DEFAULT NULL,
  created_at        DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ke_user_created (user_id, created_at),
  -- 全文检索（中文 ngram 分词），对标题和正文建联合全文索引
  FULLTEXT KEY ft_ke_title_content (title, content) WITH PARSER ngram,
  CONSTRAINT fk_ke_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_ke_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL,
  CONSTRAINT chk_ke_source CHECK (source_type IN ('upload','submission','manual')),
  CONSTRAINT chk_ke_parse CHECK (parse_status IN ('pending','parsing','done','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='知识条目表';

-- 标签查询：用生成列 + 索引支持 JSON_CONTAINS / 按标签筛选
-- （如需高频按标签过滤，可在应用层或后续迁移加生成列索引）


CREATE TABLE knowledge_links (
  source_entry_id  CHAR(36)     NOT NULL,
  target_entry_id  CHAR(36)     NOT NULL,
  link_text        VARCHAR(500) DEFAULT NULL,
  created_at       DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (source_entry_id, target_entry_id),
  KEY idx_kl_target (target_entry_id),
  CONSTRAINT fk_kl_source FOREIGN KEY (source_entry_id) REFERENCES knowledge_entries(id) ON DELETE CASCADE,
  CONSTRAINT fk_kl_target FOREIGN KEY (target_entry_id) REFERENCES knowledge_entries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='知识库双向链接表（自反 N:N）';


-- =============================================================================
-- 5. 提醒模块
-- =============================================================================

CREATE TABLE reminder_settings (
  user_id        CHAR(36)     NOT NULL,
  email          VARCHAR(255) NOT NULL,
  reminder_time  TIME         NOT NULL,
  timezone       VARCHAR(50)  DEFAULT 'Asia/Shanghai',
  enabled        TINYINT(1)   DEFAULT 1,
  channels       JSON         DEFAULT NULL,                 -- 如 ["email","browser"]
  last_sent_at   DATETIME(3)  DEFAULT NULL,
  created_at     DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id),
  KEY idx_reminder_pending (reminder_time, enabled),
  CONSTRAINT fk_reminder_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='提醒设置表（1 用户 : 1）';


-- =============================================================================
-- 6. 积分成就模块
-- =============================================================================

CREATE TABLE user_scores (
  user_id              CHAR(36)    NOT NULL,
  project_id           CHAR(36)    NOT NULL,
  total_points         INT         DEFAULT 0,
  streak_days          INT         DEFAULT 0,
  last_completed_date  DATE        DEFAULT NULL,
  updated_at           DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, project_id),
  KEY idx_scores_user (user_id, total_points),
  CONSTRAINT fk_scores_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_scores_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户积分与打卡表（1 用户 : N 项目）';


CREATE TABLE badges (
  id           CHAR(36)     NOT NULL DEFAULT (UUID()),
  code         VARCHAR(50)  NOT NULL,
  name         VARCHAR(100) NOT NULL,
  description  TEXT,
  icon_url     TEXT,
  category     VARCHAR(50)  DEFAULT 'general',
  rule_type    VARCHAR(50)  DEFAULT NULL,
  rule_value   INT          DEFAULT NULL,
  created_at   DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_badges_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='徽章定义表';


CREATE TABLE user_badges (
  user_id      CHAR(36)    NOT NULL,
  badge_id     CHAR(36)    NOT NULL,
  awarded_at   DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, badge_id),
  KEY idx_ub_user (user_id),
  CONSTRAINT fk_ub_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_ub_badge FOREIGN KEY (badge_id) REFERENCES badges(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户已获徽章表（N:N）';


-- =============================================================================
-- 7. 协作模块（阶段四，预留）
-- =============================================================================

CREATE TABLE `groups` (
  id           CHAR(36)     NOT NULL DEFAULT (UUID()),
  name         VARCHAR(255) NOT NULL,
  description  TEXT,
  owner_id     CHAR(36)     NOT NULL,
  invite_code  VARCHAR(20)  DEFAULT NULL,
  created_at   DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_groups_invite (invite_code),
  KEY idx_groups_owner (owner_id),
  CONSTRAINT fk_groups_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='学习小组表';


CREATE TABLE group_members (
  group_id  CHAR(36)    NOT NULL,
  user_id   CHAR(36)    NOT NULL,
  role      VARCHAR(20) DEFAULT 'member',
  joined_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (group_id, user_id),
  KEY idx_gm_user (user_id),
  CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE,
  CONSTRAINT fk_gm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT chk_gm_role CHECK (role IN ('owner','admin','member'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='小组成员表';


CREATE TABLE group_shares (
  id            CHAR(36)    NOT NULL DEFAULT (UUID()),
  group_id      CHAR(36)    NOT NULL,
  resource_type VARCHAR(20) DEFAULT NULL,
  resource_id   CHAR(36)    NOT NULL,
  permission    VARCHAR(20) DEFAULT 'read',
  created_at    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_gs_resource (group_id, resource_type, resource_id),
  CONSTRAINT fk_gs_group FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE,
  CONSTRAINT chk_gs_type CHECK (resource_type IN ('project','knowledge_space')),
  CONSTRAINT chk_gs_perm CHECK (permission IN ('read','comment','edit'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='小组共享资源表';


SET FOREIGN_KEY_CHECKS = 1;


-- =============================================================================
-- 8. 触发器：自动维护 updated_at（每表一个）
--    MySQL 不支持 PG 那种复用函数的触发器，必须逐表声明
-- =============================================================================

-- users
DROP TRIGGER IF EXISTS trg_users_updated;
DELIMITER $$
CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(3);
END$$
DELIMITER ;

-- user_ai_config
DROP TRIGGER IF EXISTS trg_user_ai_config_updated;
DELIMITER $$
CREATE TRIGGER trg_user_ai_config_updated BEFORE UPDATE ON user_ai_config
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(3);
END$$
DELIMITER ;

-- projects
DROP TRIGGER IF EXISTS trg_projects_updated;
DELIMITER $$
CREATE TRIGGER trg_projects_updated BEFORE UPDATE ON projects
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(3);
END$$
DELIMITER ;

-- phases
DROP TRIGGER IF EXISTS trg_phases_updated;
DELIMITER $$
CREATE TRIGGER trg_phases_updated BEFORE UPDATE ON phases
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(3);
END$$
DELIMITER ;

-- tasks
DROP TRIGGER IF EXISTS trg_tasks_updated;
DELIMITER $$
CREATE TRIGGER trg_tasks_updated BEFORE UPDATE ON tasks
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(3);
END$$
DELIMITER ;

-- submissions
DROP TRIGGER IF EXISTS trg_submissions_updated;
DELIMITER $$
CREATE TRIGGER trg_submissions_updated BEFORE UPDATE ON submissions
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(3);
END$$
DELIMITER ;

-- knowledge_entries
DROP TRIGGER IF EXISTS trg_ke_updated;
DELIMITER $$
CREATE TRIGGER trg_ke_updated BEFORE UPDATE ON knowledge_entries
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(3);
END$$
DELIMITER ;

-- reminder_settings
DROP TRIGGER IF EXISTS trg_reminder_updated;
DELIMITER $$
CREATE TRIGGER trg_reminder_updated BEFORE UPDATE ON reminder_settings
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(3);
END$$
DELIMITER ;

-- user_scores
DROP TRIGGER IF EXISTS trg_scores_updated;
DELIMITER $$
CREATE TRIGGER trg_scores_updated BEFORE UPDATE ON user_scores
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(3);
END$$
DELIMITER ;

-- groups
DROP TRIGGER IF EXISTS trg_groups_updated;
DELIMITER $$
CREATE TRIGGER trg_groups_updated BEFORE UPDATE ON `groups`
FOR EACH ROW
BEGIN
  SET NEW.updated_at = CURRENT_TIMESTAMP(3);
END$$
DELIMITER ;


-- =============================================================================
-- 9. 提交审核后自动同步总结到知识库（AFTER INSERT 触发器）
-- =============================================================================

DROP TRIGGER IF EXISTS trg_submissions_sync_kb;
DELIMITER $$
CREATE TRIGGER trg_submissions_sync_kb
AFTER INSERT ON submissions
FOR EACH ROW
BEGIN
  DECLARE v_project_id CHAR(36) DEFAULT NULL;
  DECLARE v_user_id    CHAR(36) DEFAULT NULL;
  DECLARE v_task_title VARCHAR(500) DEFAULT '';
  DECLARE v_entry_title VARCHAR(600);

  SELECT project_id, user_id, COALESCE(title, '')
    INTO v_project_id, v_user_id, v_task_title
  FROM tasks
  WHERE id = NEW.task_id
  LIMIT 1;

  SET v_entry_title = CONCAT('提交记录：', v_task_title);

  INSERT INTO knowledge_entries (
    user_id, project_id, title, content, content_md,
    source_type, source_id, tags, word_count, parse_status
  ) VALUES (
    v_user_id, v_project_id,
    v_entry_title,
    NEW.content, NEW.content,
    'submission', NEW.id,
    JSON_ARRAY('提交记录'),
    CHAR_LENGTH(NEW.content),
    'done'
  );
END$$
DELIMITER ;


-- =============================================================================
-- 10. 初始数据（默认徽章）
-- =============================================================================

INSERT INTO badges (code, name, description, category, rule_type, rule_value) VALUES
  ('first_pass',       '初露锋芒',  '第一次通过 AI 审核',           'special', 'count',   1),
  ('streak_7',         '坚持一周',  '连续打卡 7 天',                'streak',  'streak',  7),
  ('streak_30',        '月度精进',  '连续打卡 30 天',               'streak',  'streak',  30),
  ('score_100',        '百点成就',  '累计积分达 100',               'score',   'score',   100),
  ('score_500',        '学霸之路',  '累计积分达 500',               'score',   'score',   500),
  ('kb_10',            '知识收藏家','知识库条目达 10 篇',           'count',   'kb',      10),
  ('perfect_review_5', '完美五连',  '连续 5 次拿到 10 分',          'streak',  'perfect', 5)
ON DUPLICATE KEY UPDATE name = VALUES(name);


-- =============================================================================
-- 11. 自检验证（执行后可跑一遍）
-- =============================================================================
-- 表数量（应为 15）
-- SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();
--
-- 触发器列表
-- SELECT trigger_name, event_object_table FROM information_schema.triggers
--   WHERE trigger_schema = DATABASE() ORDER BY event_object_table;
--
-- 全文索引
-- SELECT index_name, table_name FROM information_schema.statistics
--   WHERE index_type = 'FULLTEXT' AND table_schema = DATABASE();
--
-- 徽章数量（应为 7）
-- SELECT COUNT(*) FROM badges;
