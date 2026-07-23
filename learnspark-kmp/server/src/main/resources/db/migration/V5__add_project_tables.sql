-- 阶段 3.1：项目 / 阶段 / 任务 / 提交表（按文档 §十一.2.3）
-- 旧版 Vue3 已存在这些表，新版需要重建并接入同步引擎。
-- V3 已经预置了 version 字段。

CREATE TABLE projects (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    goal            TEXT,
    cover_color     VARCHAR(20),                        -- hex 颜色（迁移字段映射：不变）
    daily_hours     INT NOT NULL DEFAULT 2,
    is_ai_generated TINYINT(1) NOT NULL DEFAULT 0,      -- 迁移字段映射：0/1 → boolean
    status          VARCHAR(20) NOT NULL DEFAULT 'active',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_projects_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_projects_user (user_id),
    INDEX idx_projects_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE phases (
    id              VARCHAR(36) PRIMARY KEY,
    project_id      VARCHAR(36) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    sort_order      INT NOT NULL DEFAULT 0,
    start_date      DATE,
    end_date        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_phases_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    INDEX idx_phases_project (project_id),
    INDEX idx_phases_sort (project_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tasks (
    id              VARCHAR(36) PRIMARY KEY,
    phase_id        VARCHAR(36) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    sort_order      INT NOT NULL DEFAULT 0,
    estimated_hours INT NOT NULL DEFAULT 1,
    actual_hours    INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending / in_progress / done / skipped
    due_date        DATE,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_tasks_phase FOREIGN KEY (phase_id) REFERENCES phases(id) ON DELETE CASCADE,
    INDEX idx_tasks_phase (phase_id),
    INDEX idx_tasks_status (status),
    INDEX idx_tasks_due (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE submissions (
    id              VARCHAR(36) PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    content         MEDIUMTEXT NOT NULL,                 -- 字段映射：旧版 content_md → 新版 content
    ai_score        INT,
    ai_feedback     TEXT,
    ai_highlights   TEXT,
    reviewed_at     DATETIME(3),
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_submissions_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_submissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_submissions_task (task_id),
    INDEX idx_submissions_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
