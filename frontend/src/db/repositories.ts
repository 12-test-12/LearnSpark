/**
 * ============================================================
 *  LearnSpark · 本地 Repository 集合
 *
 *  把后端 Service 层的逻辑全部搬到前端
 *  所有数据操作通过 SQLite 完成，不需要服务器
 *
 *  字段命名约定：
 *    数据库：snake_case（如 daily_hours）
 *    前端：  camelCase（如 dailyHours）
 *    Repository 负责两层之间的转换
 *
 *  user_id 固定为 'local-user'（离线单用户）
 * ============================================================
 */

import { BaseRepository, type BaseEntity } from './base-repository'
import { getDatabase, uuid, now, today } from './database'

// ============================================================
// 工具函数：snake_case ↔ camelCase 转换
// ============================================================

function toCamelCase(str: string): string {
  return str.replace(/_([a-z])/g, (_, c) => c.toUpperCase())
}

function toSnakeCase(str: string): string {
  return str.replace(/[A-Z]/g, (c) => '_' + c.toLowerCase())
}

/** 把数据库行（snake_case）转成前端对象（camelCase） */
function rowToEntity<T>(row: Record<string, unknown>): T {
  const result: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(row)) {
    result[toCamelCase(key)] = value
  }
  return result as T
}

/** 把前端对象（camelCase）转成数据库行（snake_case） */
function entityToRow(obj: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(obj)) {
    result[toSnakeCase(key)] = value
  }
  return result
}

const LOCAL_USER_ID = 'local-user'

// ============================================================
// 1. 项目 Repository
// ============================================================

interface ProjectRow extends BaseEntity {
  user_id: string
  name: string
  description: string | null
  goal: string | null
  daily_hours: number
  is_ai_generated: number
  status: string
  cover_color: string
  deleted_at: string | null
}

class ProjectRepository extends BaseRepository<ProjectRow> {
  constructor() {
    super('projects')
  }

  /** 查询所有未删除的项目 */
  async listActive(): Promise<ProjectRow[]> {
    return this.findAll(
      'user_id = ? AND deleted_at IS NULL',
      [LOCAL_USER_ID],
      'created_at DESC'
    )
  }

  /** 创建项目 */
  async createProject(data: {
    name: string
    description?: string
    goal?: string
    dailyHours?: number
    coverColor?: string
    isAiGenerated?: boolean
  }): Promise<ProjectRow> {
    const id = await this.insert({
      user_id: LOCAL_USER_ID,
      name: data.name,
      description: data.description ?? null,
      goal: data.goal ?? null,
      daily_hours: data.dailyHours ?? 2,
      is_ai_generated: data.isAiGenerated ? 1 : 0,
      status: 'active',
      cover_color: data.coverColor ?? '#18a058',
      deleted_at: null,
    } as unknown as Partial<ProjectRow> & Record<string, unknown>)
    return (await this.findById(id))!
  }

  /** 更新项目 */
  async updateProject(id: string, data: {
    name?: string
    description?: string
    goal?: string
    dailyHours?: number
    coverColor?: string
  }): Promise<void> {
    const updateData: Record<string, unknown> = {}
    if (data.name !== undefined) updateData.name = data.name
    if (data.description !== undefined) updateData.description = data.description
    if (data.goal !== undefined) updateData.goal = data.goal
    if (data.dailyHours !== undefined) updateData.daily_hours = data.dailyHours
    if (data.coverColor !== undefined) updateData.cover_color = data.coverColor
    await this.update(id, updateData as Partial<ProjectRow>)
  }

  /** 软删除项目 */
  async softDeleteProject(id: string): Promise<void> {
    await this.softDelete(id)
  }
}

// ============================================================
// 2. 阶段 Repository
// ============================================================

interface PhaseRow extends BaseEntity {
  project_id: string
  name: string
  objective: string | null
  sort_order: number
}

class PhaseRepository extends BaseRepository<PhaseRow> {
  constructor() {
    super('phases')
  }

  async listByProject(projectId: string): Promise<PhaseRow[]> {
    return this.findAll('project_id = ?', [projectId], 'sort_order ASC')
  }

  async createPhase(data: {
    projectId: string
    name: string
    objective?: string
    sortOrder?: number
  }): Promise<PhaseRow> {
    const id = await this.insert({
      project_id: data.projectId,
      name: data.name,
      objective: data.objective ?? null,
      sort_order: data.sortOrder ?? 0,
    } as unknown as Partial<PhaseRow>)
    return (await this.findById(id))!
  }
}

// ============================================================
// 3. 任务 Repository
// ============================================================

interface TaskRow extends BaseEntity {
  phase_id: string | null
  project_id: string
  day_number: number | null
  title: string | null
  description: string
  verification_criteria: string | null
  status: string
  due_date: string | null
  completed_at: string | null
  sort_order: number
  deleted_at: string | null
}

class TaskRepository extends BaseRepository<TaskRow> {
  constructor() {
    super('tasks')
  }

  /** 查询项目下所有未删除任务 */
  async listByProject(projectId: string, date?: string): Promise<TaskRow[]> {
    if (date) {
      return this.findAll(
        'project_id = ? AND deleted_at IS NULL AND due_date = ?',
        [projectId, date],
        'sort_order ASC'
      )
    }
    return this.findAll(
      'project_id = ? AND deleted_at IS NULL',
      [projectId],
      'sort_order ASC'
    )
  }

  /** 创建任务 */
  async createTask(data: {
    projectId: string
    phaseId?: string | null
    dayNumber?: number | null
    title?: string
    description: string
    verificationCriteria?: string
    dueDate?: string | null
    sortOrder?: number
  }): Promise<TaskRow> {
    const id = await this.insert({
      project_id: data.projectId,
      phase_id: data.phaseId ?? null,
      day_number: data.dayNumber ?? null,
      title: data.title ?? null,
      description: data.description,
      verification_criteria: data.verificationCriteria ?? null,
      status: 'pending',
      due_date: data.dueDate ?? null,
      completed_at: null,
      sort_order: data.sortOrder ?? 0,
      deleted_at: null,
    } as unknown as Partial<TaskRow>)
    return (await this.findById(id))!
  }

  /** 更新任务状态 */
  async updateStatus(id: string, status: string): Promise<void> {
    const updateData: Record<string, unknown> = { status }
    if (status === 'passed' || status === 'failed') {
      updateData.completed_at = now()
    }
    await this.update(id, updateData as Partial<TaskRow>)
  }
}

// ============================================================
// 4. 提交记录 Repository
// ============================================================

interface SubmissionRow extends BaseEntity {
  task_id: string
  user_id: string
  content: string
  attachment_urls: string | null
  ai_feedback: string | null
  ai_score: number | null
  passed: number | null
  ai_model: string | null
  ai_raw_response: string | null
  submitted_at: string
  reviewed_at: string | null
}

class SubmissionRepository extends BaseRepository<SubmissionRow> {
  constructor() {
    super('submissions')
  }

  async createSubmission(data: {
    taskId: string
    content: string
    attachmentUrls?: string[]
  }): Promise<SubmissionRow> {
    const id = await this.insert({
      task_id: data.taskId,
      user_id: LOCAL_USER_ID,
      content: data.content,
      attachment_urls: data.attachmentUrls ? JSON.stringify(data.attachmentUrls) : null,
      ai_feedback: null,
      ai_score: null,
      passed: null,
      ai_model: null,
      ai_raw_response: null,
      submitted_at: now(),
      reviewed_at: null,
    } as unknown as Partial<SubmissionRow>)
    return (await this.findById(id))!
  }

  /** 更新 AI 审核结果 */
  async updateAiResult(id: string, result: {
    feedback: string
    score: number
    passed: boolean
    model: string
    rawResponse?: unknown
  }): Promise<void> {
    await this.update(id, {
      ai_feedback: result.feedback,
      ai_score: result.score,
      passed: result.passed ? 1 : 0,
      ai_model: result.model,
      ai_raw_response: result.rawResponse ? JSON.stringify(result.rawResponse) : null,
      reviewed_at: now(),
    } as Partial<SubmissionRow>)
  }
}

// ============================================================
// 5. 知识库 Repository
// ============================================================

interface KnowledgeEntryRow extends BaseEntity {
  user_id: string
  project_id: string | null
  title: string
  content: string | null
  content_md: string | null
  summary: string | null
  source_type: string | null
  source_id: string | null
  file_path: string | null
  mime_type: string | null
  tags: string | null
  word_count: number
  parse_status: string
  deleted_at: string | null
}

class KnowledgeRepository extends BaseRepository<KnowledgeEntryRow> {
  constructor() {
    super('knowledge_entries')
  }

  async listAll(projectId?: string): Promise<KnowledgeEntryRow[]> {
    if (projectId) {
      return this.findAll(
        'user_id = ? AND deleted_at IS NULL AND (project_id = ? OR project_id IS NULL)',
        [LOCAL_USER_ID, projectId],
        'created_at DESC'
      )
    }
    return this.findAll(
      'user_id = ? AND deleted_at IS NULL',
      [LOCAL_USER_ID],
      'created_at DESC'
    )
  }

  async createEntry(data: {
    projectId?: string | null
    title: string
    content?: string
    contentMd?: string
    summary?: string
    sourceType?: string
    tags?: string[]
    filePath?: string
    mimeType?: string
  }): Promise<KnowledgeEntryRow> {
    const content = data.content ?? data.contentMd ?? ''
    const id = await this.insert({
      user_id: LOCAL_USER_ID,
      project_id: data.projectId ?? null,
      title: data.title,
      content,
      content_md: data.contentMd ?? data.content ?? '',
      summary: data.summary ?? null,
      source_type: data.sourceType ?? 'manual',
      source_id: null,
      file_path: data.filePath ?? null,
      mime_type: data.mimeType ?? null,
      tags: data.tags ? JSON.stringify(data.tags) : null,
      word_count: content.length,
      parse_status: 'done',
      deleted_at: null,
    } as unknown as Partial<KnowledgeEntryRow>)
    return (await this.findById(id))!
  }

  /** 搜索知识条目（SQLite LIKE，替代 MySQL FULLTEXT） */
  async search(keyword: string): Promise<KnowledgeEntryRow[]> {
    return this.findAll(
      'user_id = ? AND deleted_at IS NULL AND (title LIKE ? OR content LIKE ?)',
      [LOCAL_USER_ID, `%${keyword}%`, `%${keyword}%`],
      'created_at DESC'
    )
  }
}

// ============================================================
// 6. 积分 & 徽章 Repository
// ============================================================

interface UserScoreRow {
  user_id: string
  project_id: string
  total_points: number
  streak_days: number
  last_completed_date: string | null
  updated_at: string
}

class GamificationRepository {
  /** 获取用户在某项目的积分 */
  async getScore(projectId: string): Promise<UserScoreRow | null> {
    const db = await getDatabase()
    const result = await db.query(
      'SELECT * FROM user_scores WHERE user_id = ? AND project_id = ?',
      [LOCAL_USER_ID, projectId]
    )
    return (result.values?.[0] as UserScoreRow) ?? null
  }

  /** 增加积分 + 更新打卡 */
  async addPoints(projectId: string, points: number): Promise<UserScoreRow> {
    const db = await getDatabase()
    const existing = await this.getScore(projectId)
    const todayStr = today()

    if (existing) {
      // 计算连续打卡
      let streak = existing.streak_days
      const lastDate = existing.last_completed_date
      if (lastDate !== todayStr) {
        // 检查昨天是否打卡
        const yesterday = new Date()
        yesterday.setDate(yesterday.getDate() - 1)
        const yesterdayStr = yesterday.toISOString().substring(0, 10)
        streak = lastDate === yesterdayStr ? streak + 1 : 1
      }

      await db.run(
        `UPDATE user_scores SET total_points = ?, streak_days = ?, last_completed_date = ?, updated_at = ? WHERE user_id = ? AND project_id = ?`,
        [existing.total_points + points, streak, todayStr, now(), LOCAL_USER_ID, projectId]
      )
    } else {
      await db.run(
        `INSERT INTO user_scores (user_id, project_id, total_points, streak_days, last_completed_date, updated_at) VALUES (?, ?, ?, ?, ?, ?)`,
        [LOCAL_USER_ID, projectId, points, 1, todayStr, now()]
      )
    }
    return (await this.getScore(projectId))!
  }

  /** 获取所有项目的总积分 */
  async getTotalPoints(): Promise<number> {
    const db = await getDatabase()
    const result = await db.query(
      'SELECT COALESCE(SUM(total_points), 0) as total FROM user_scores WHERE user_id = ?',
      [LOCAL_USER_ID]
    )
    return (result.values?.[0]?.total as number) ?? 0
  }

  /** 获取所有徽章定义 */
  async listBadges() {
    const db = await getDatabase()
    const result = await db.query('SELECT * FROM badges ORDER BY category, rule_value')
    return result.values ?? []
  }

  /** 获取用户已获徽章 */
  async listUserBadges() {
    const db = await getDatabase()
    const result = await db.query(
      `SELECT b.*, ub.awarded_at FROM badges b
       INNER JOIN user_badges ub ON b.id = ub.badge_id
       WHERE ub.user_id = ?
       ORDER BY ub.awarded_at DESC`,
      [LOCAL_USER_ID]
    )
    return result.values ?? []
  }

  /** 颁发徽章 */
  async awardBadge(badgeId: string): Promise<void> {
    const db = await getDatabase()
    await db.run(
      'INSERT OR IGNORE INTO user_badges (user_id, badge_id, awarded_at) VALUES (?, ?, ?)',
      [LOCAL_USER_ID, badgeId, now()]
    )
  }
}

// ============================================================
// 7. AI 配置 Repository
// ============================================================

interface AiConfigRow {
  user_id: string
  deepseek_api_key: string | null
  deepseek_base_url: string
  deepseek_model: string
  search_api_key: string | null
  search_provider: string
  local_mode: number
  embedding_model: string
  updated_at: string
}

class AiConfigRepository {
  async getConfig(): Promise<AiConfigRow | null> {
    const db = await getDatabase()
    const result = await db.query('SELECT * FROM user_ai_config WHERE user_id = ?', [LOCAL_USER_ID])
    return (result.values?.[0] as AiConfigRow) ?? null
  }

  async saveConfig(data: {
    apiKey?: string
    baseUrl?: string
    model?: string
    searchApiKey?: string
    searchProvider?: string
  }): Promise<void> {
    const db = await getDatabase()
    const existing = await this.getConfig()
    if (existing) {
      await db.run(
        `UPDATE user_ai_config SET
          deepseek_api_key = ?,
          deepseek_base_url = ?,
          deepseek_model = ?,
          search_api_key = ?,
          search_provider = ?,
          updated_at = ?
        WHERE user_id = ?`,
        [
          data.apiKey ?? existing.deepseek_api_key,
          data.baseUrl ?? existing.deepseek_base_url,
          data.model ?? existing.deepseek_model,
          data.searchApiKey ?? existing.search_api_key,
          data.searchProvider ?? existing.search_provider,
          now(),
          LOCAL_USER_ID,
        ]
      )
    } else {
      await db.run(
        `INSERT INTO user_ai_config (user_id, deepseek_api_key, deepseek_base_url, deepseek_model, search_api_key, search_provider, local_mode, embedding_model, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, 0, 'bge-large-zh', ?, ?)`,
        [
          LOCAL_USER_ID,
          data.apiKey ?? null,
          data.baseUrl ?? 'https://api.deepseek.com/v1',
          data.model ?? 'deepseek-chat',
          data.searchApiKey ?? null,
          data.searchProvider ?? 'bing',
          now(),
          now(),
        ]
      )
    }
  }
}

// ============================================================
// 8. 统计 Repository
// ============================================================

class StatsRepository {
  /** 获取用户活动热力图数据（最近 N 天的打卡情况） */
  async getActivityHeatmap(days: number = 90): Promise<{ date: string; count: number }[]> {
    const db = await getDatabase()
    const result = await db.query(
      `SELECT date(completed_at) as date, COUNT(*) as count
       FROM tasks
       WHERE user_id IS NULL AND completed_at IS NOT NULL
         AND date(completed_at) >= date('now', ?)
       GROUP BY date(completed_at)
       ORDER BY date ASC`,
      [`-${days} days`]
    )
    return (result.values ?? []) as { date: string; count: number }[]
  }

  /** 获取总任务完成数 */
  async getCompletedTaskCount(): Promise<number> {
    const db = await getDatabase()
    const result = await db.query(
      "SELECT COUNT(*) as cnt FROM tasks WHERE status IN ('passed','failed') AND deleted_at IS NULL"
    )
    return (result.values?.[0]?.cnt as number) ?? 0
  }

  /** 获取最长连续打卡天数 */
  async getMaxStreak(): Promise<number> {
    const db = await getDatabase()
    const result = await db.query(
      'SELECT MAX(streak_days) as max_streak FROM user_scores WHERE user_id = ?',
      [LOCAL_USER_ID]
    )
    return (result.values?.[0]?.max_streak as number) ?? 0
  }

  /** 获取知识库条目数 */
  async getKnowledgeCount(): Promise<number> {
    const db = await getDatabase()
    const result = await db.query(
      'SELECT COUNT(*) as cnt FROM knowledge_entries WHERE user_id = ? AND deleted_at IS NULL',
      [LOCAL_USER_ID]
    )
    return (result.values?.[0]?.cnt as number) ?? 0
  }
}

// ============================================================
// 导出单例
// ============================================================

export const projectRepo = new ProjectRepository()
export const phaseRepo = new PhaseRepository()
export const taskRepo = new TaskRepository()
export const submissionRepo = new SubmissionRepository()
export const knowledgeRepo = new KnowledgeRepository()
export const gamificationRepo = new GamificationRepository()
export const aiConfigRepo = new AiConfigRepository()
export const statsRepo = new StatsRepository()

export {
  rowToEntity,
  entityToRow,
  LOCAL_USER_ID,
  type ProjectRow,
  type PhaseRow,
  type TaskRow,
  type SubmissionRow,
  type KnowledgeEntryRow,
  type AiConfigRow,
  type UserScoreRow,
}
