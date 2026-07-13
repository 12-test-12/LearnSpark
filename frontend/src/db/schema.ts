 /**
 * ============================================================
 *  LearnSpark · SQLite Schema
 *  从 MySQL V1__init.sql 转换而来
 *
 *  关键转换：
 *  1. VARCHAR(N) → TEXT（SQLite 不强制长度）
 *  2. DATETIME(3) → TEXT（ISO8601 字符串，SQLite 无原生日期类型）
 *  3. TINYINT(1) → INTEGER（0/1 表示布尔）
 *  4. JSON → TEXT（JSON 字符串存储）
 *  5. MEDIUMTEXT → TEXT
 *  6. UUID() → 由应用层生成（crypto.randomUUID()）
 *  7. ENGINE/CHARSET → 去除（SQLite 不支持）
 *  8. FULLTEXT INDEX → 去除（SQLite FTS5 单独建虚拟表）
 *  9. 外键约束保留，但需要 PRAGMA foreign_keys = ON
 *
 *  离线简化：
 *  - 去掉 users 表的 password_hash（无认证）
 *  - user_id 固定为 'local-user'（单用户）
 *  - 去掉 groups / group_members / group_shares（协作模块离线不需要）
 * ============================================================
 */

import type { DBSQLiteValues, SQLiteDBConnection } from '@capacitor-community/sqlite'

/**
 * 建表 SQL 数组（按依赖顺序）
 * 离线版只保留 12 张表（去掉 users 简化、去掉 3 张协作表）
 */
export const SCHEMA_STATEMENTS: string[] = [
  // ============================================================
  // 0. 用户表（离线版简化：只存 1 个本地用户，无密码）
  // ============================================================
  `CREATE TABLE IF NOT EXISTS users (
    id              TEXT PRIMARY KEY DEFAULT 'local-user',
    email           TEXT,
    nickname        TEXT DEFAULT '我',
    avatar_url      TEXT,
    timezone        TEXT DEFAULT 'Asia/Shanghai',
    status          TEXT DEFAULT 'active',
    last_login_at   TEXT,
    created_at      TEXT DEFAULT (datetime('now')),
    updated_at      TEXT DEFAULT (datetime('now'))
  )`,

  // ============================================================
  // 1. AI 配置表（1:1）
  // ============================================================
  `CREATE TABLE IF NOT EXISTS user_ai_config (
    user_id                    TEXT PRIMARY KEY DEFAULT 'local-user',
    deepseek_api_key           TEXT,
    deepseek_base_url          TEXT DEFAULT 'https://api.deepseek.com/v1',
    deepseek_model             TEXT DEFAULT 'deepseek-chat',
    search_api_key             TEXT,
    search_provider            TEXT DEFAULT 'bing',
    local_mode                 INTEGER DEFAULT 0,
    embedding_model            TEXT DEFAULT 'bge-large-zh',
    created_at                 TEXT DEFAULT (datetime('now')),
    updated_at                 TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  )`,

  // ============================================================
  // 2. 学习项目表
  // ============================================================
  `CREATE TABLE IF NOT EXISTS projects (
    id              TEXT PRIMARY KEY,
    user_id         TEXT DEFAULT 'local-user',
    name            TEXT NOT NULL,
    description     TEXT,
    goal            TEXT,
    daily_hours     INTEGER DEFAULT 2,
    is_ai_generated INTEGER DEFAULT 0,
    status          TEXT DEFAULT 'active',
    cover_color     TEXT DEFAULT '#18a058',
    deleted_at      TEXT,
    created_at      TEXT DEFAULT (datetime('now')),
    updated_at      TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  )`,
  `CREATE INDEX IF NOT EXISTS idx_projects_user_id ON projects(user_id)`,
  `CREATE INDEX IF NOT EXISTS idx_projects_status ON projects(status)`,

  // ============================================================
  // 3. 学习阶段表
  // ============================================================
  `CREATE TABLE IF NOT EXISTS phases (
    id          TEXT PRIMARY KEY,
    project_id  TEXT NOT NULL,
    name        TEXT NOT NULL,
    objective   TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT DEFAULT (datetime('now')),
    updated_at  TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
  )`,
  `CREATE INDEX IF NOT EXISTS idx_phases_project_order ON phases(project_id, sort_order)`,

  // ============================================================
  // 4. 任务表
  // ============================================================
  `CREATE TABLE IF NOT EXISTS tasks (
    id                    TEXT PRIMARY KEY,
    phase_id              TEXT,
    project_id            TEXT NOT NULL,
    day_number            INTEGER,
    title                 TEXT,
    description           TEXT NOT NULL,
    verification_criteria TEXT,
    status                TEXT DEFAULT 'pending',
    due_date              TEXT,
    completed_at          TEXT,
    sort_order            INTEGER DEFAULT 0,
    deleted_at            TEXT,
    created_at            TEXT DEFAULT (datetime('now')),
    updated_at            TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (phase_id) REFERENCES phases(id) ON DELETE SET NULL
  )`,
  `CREATE INDEX IF NOT EXISTS idx_tasks_project_status ON tasks(project_id, status)`,
  `CREATE INDEX IF NOT EXISTS idx_tasks_due_date ON tasks(due_date)`,
  `CREATE INDEX IF NOT EXISTS idx_tasks_phase_order ON tasks(phase_id, sort_order)`,

  // ============================================================
  // 5. 任务提交记录表
  // ============================================================
  `CREATE TABLE IF NOT EXISTS submissions (
    id              TEXT PRIMARY KEY,
    task_id         TEXT NOT NULL,
    user_id         TEXT DEFAULT 'local-user',
    content         TEXT NOT NULL,
    attachment_urls TEXT,
    ai_feedback     TEXT,
    ai_score        INTEGER,
    passed          INTEGER,
    ai_model        TEXT,
    ai_raw_response TEXT,
    submitted_at    TEXT DEFAULT (datetime('now')),
    reviewed_at     TEXT,
    created_at      TEXT DEFAULT (datetime('now')),
    updated_at      TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  )`,
  `CREATE INDEX IF NOT EXISTS idx_submissions_task ON submissions(task_id, submitted_at)`,
  `CREATE INDEX IF NOT EXISTS idx_submissions_user_time ON submissions(user_id, submitted_at)`,

  // ============================================================
  // 6. 知识条目表
  // ============================================================
  `CREATE TABLE IF NOT EXISTS knowledge_entries (
    id                TEXT PRIMARY KEY,
    user_id           TEXT DEFAULT 'local-user',
    project_id        TEXT,
    title             TEXT NOT NULL,
    content           TEXT,
    content_md        TEXT,
    summary           TEXT,
    source_type       TEXT,
    source_id         TEXT,
    file_path         TEXT,
    mime_type         TEXT,
    tags              TEXT,
    word_count        INTEGER DEFAULT 0,
    parse_status      TEXT DEFAULT 'done',
    vector_embedding  TEXT,
    deleted_at        TEXT,
    created_at        TEXT DEFAULT (datetime('now')),
    updated_at        TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL
  )`,
  `CREATE INDEX IF NOT EXISTS idx_ke_user_created ON knowledge_entries(user_id, created_at)`,

  // ============================================================
  // 7. 知识库双向链接表
  // ============================================================
  `CREATE TABLE IF NOT EXISTS knowledge_links (
    source_entry_id  TEXT NOT NULL,
    target_entry_id  TEXT NOT NULL,
    link_text        TEXT,
    created_at       TEXT DEFAULT (datetime('now')),
    PRIMARY KEY (source_entry_id, target_entry_id),
    FOREIGN KEY (source_entry_id) REFERENCES knowledge_entries(id) ON DELETE CASCADE,
    FOREIGN KEY (target_entry_id) REFERENCES knowledge_entries(id) ON DELETE CASCADE
  )`,
  `CREATE INDEX IF NOT EXISTS idx_kl_target ON knowledge_links(target_entry_id)`,

  // ============================================================
  // 8. 提醒设置表（离线版：改为本地通知配置）
  // ============================================================
  `CREATE TABLE IF NOT EXISTS reminder_settings (
    user_id        TEXT PRIMARY KEY DEFAULT 'local-user',
    reminder_time  TEXT NOT NULL,
    timezone       TEXT DEFAULT 'Asia/Shanghai',
    enabled        INTEGER DEFAULT 1,
    channels       TEXT,
    last_sent_at   TEXT,
    created_at     TEXT DEFAULT (datetime('now')),
    updated_at     TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  )`,
  `CREATE INDEX IF NOT EXISTS idx_reminder_pending ON reminder_settings(reminder_time, enabled)`,

  // ============================================================
  // 9. 用户积分与打卡表
  // ============================================================
  `CREATE TABLE IF NOT EXISTS user_scores (
    user_id              TEXT NOT NULL DEFAULT 'local-user',
    project_id           TEXT NOT NULL,
    total_points         INTEGER DEFAULT 0,
    streak_days          INTEGER DEFAULT 0,
    last_completed_date  TEXT,
    updated_at           TEXT DEFAULT (datetime('now')),
    PRIMARY KEY (user_id, project_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
  )`,
  `CREATE INDEX IF NOT EXISTS idx_scores_user ON user_scores(user_id, total_points)`,

  // ============================================================
  // 10. 徽章定义表
  // ============================================================
  `CREATE TABLE IF NOT EXISTS badges (
    id           TEXT PRIMARY KEY,
    code         TEXT NOT NULL UNIQUE,
    name         TEXT NOT NULL,
    description  TEXT,
    icon_url     TEXT,
    category     TEXT DEFAULT 'general',
    rule_type    TEXT,
    rule_value   INTEGER,
    created_at   TEXT DEFAULT (datetime('now'))
  )`,

  // ============================================================
  // 11. 用户已获徽章表
  // ============================================================
  `CREATE TABLE IF NOT EXISTS user_badges (
    user_id      TEXT NOT NULL DEFAULT 'local-user',
    badge_id     TEXT NOT NULL,
    awarded_at   TEXT DEFAULT (datetime('now')),
    PRIMARY KEY (user_id, badge_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (badge_id) REFERENCES badges(id) ON DELETE CASCADE
  )`,
  `CREATE INDEX IF NOT EXISTS idx_ub_user ON user_badges(user_id)`,
]

/**
 * 默认徽章初始数据
 */
export const SEED_DATA: { sql: string; values: unknown[] }[] = [
  {
    sql: `INSERT OR IGNORE INTO badges (id, code, name, description, category, rule_type, rule_value) VALUES (?,?,?,?,?,?,?)`,
    values: [
      crypto.randomUUID(), 'first_pass', '初露锋芒', '第一次通过 AI 审核', 'special', 'count', 1,
    ],
  },
  {
    sql: `INSERT OR IGNORE INTO badges (id, code, name, description, category, rule_type, rule_value) VALUES (?,?,?,?,?,?,?)`,
    values: [
      crypto.randomUUID(), 'streak_7', '坚持一周', '连续打卡 7 天', 'streak', 'streak', 7,
    ],
  },
  {
    sql: `INSERT OR IGNORE INTO badges (id, code, name, description, category, rule_type, rule_value) VALUES (?,?,?,?,?,?,?)`,
    values: [
      crypto.randomUUID(), 'streak_30', '月度精进', '连续打卡 30 天', 'streak', 'streak', 30,
    ],
  },
  {
    sql: `INSERT OR IGNORE INTO badges (id, code, name, description, category, rule_type, rule_value) VALUES (?,?,?,?,?,?,?)`,
    values: [
      crypto.randomUUID(), 'score_100', '百点成就', '累计积分达 100', 'score', 'score', 100,
    ],
  },
  {
    sql: `INSERT OR IGNORE INTO badges (id, code, name, description, category, rule_type, rule_value) VALUES (?,?,?,?,?,?,?)`,
    values: [
      crypto.randomUUID(), 'score_500', '学霸之路', '累计积分达 500', 'score', 'score', 500,
    ],
  },
  {
    sql: `INSERT OR IGNORE INTO badges (id, code, name, description, category, rule_type, rule_value) VALUES (?,?,?,?,?,?,?)`,
    values: [
      crypto.randomUUID(), 'kb_10', '知识收藏家', '知识库条目达 10 篇', 'count', 'kb', 10,
    ],
  },
  {
    sql: `INSERT OR IGNORE INTO badges (id, code, name, description, category, rule_type, rule_value) VALUES (?,?,?,?,?,?,?)`,
    values: [
      crypto.randomUUID(), 'perfect_review_5', '完美五连', '连续 5 次拿到 10 分', 'streak', 'perfect', 5,
    ],
  },
  // 初始化本地用户
  {
    sql: `INSERT OR IGNORE INTO users (id, nickname) VALUES ('local-user', '我')`,
    values: [],
  },
]

/**
 * 数据库初始化：执行所有建表语句 + 插入种子数据
 */
export async function initializeSchema(db: SQLiteDBConnection): Promise<void> {
  // 开启外键约束
  await db.execute('PRAGMA foreign_keys = ON')

  // 执行建表
  for (const stmt of SCHEMA_STATEMENTS) {
    await db.execute(stmt)
  }

  // 插入种子数据
  for (const seed of SEED_DATA) {
    if (seed.values.length > 0) {
      await db.run(seed.sql, seed.values)
    } else {
      await db.execute(seed.sql)
    }
  }
}

export type { DBSQLiteValues }
