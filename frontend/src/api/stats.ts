/**
 * 统计 API（离线版）— 接口签名不变，底层改为 SQLite
 */
import { statsRepo, taskRepo, gamificationRepo } from '@/db/repositories'
import { today } from '@/db/database'

export interface ProjectStats {
  totalTasks: number
  passedTasks: number
  completionRate: number
  streakDays: number
  totalPoints: number
}

export interface UserStats {
  totalPoints: number
  maxStreakDays: number
  knowledgeCount: number
  totalSubmissions: number
  passedSubmissions: number
  approvalRate: number
}

export interface DashboardTask {
  id: string
  phaseId: string | null
  projectId: string
  dayNumber: number | null
  title: string | null
  description: string
  verificationCriteria: string | null
  status: string
  dueDate: string | null
  completedAt: string | null
  sortOrder: number | null
  createdAt: string
  updatedAt: string
}

export interface DashboardData {
  todayPendingCount: number
  todayTasks: DashboardTask[]
  maxStreakDays: number
  totalPoints: number
  knowledgeCount: number
  dailyQuote: string
}

export interface DailyActivity {
  date: string
  count: number
}

const DAILY_QUOTES = [
  '日拱一卒，功不唐捐',
  '学而时习之，不亦说乎',
  '不积跬步，无以至千里',
  '今日事，今日毕',
  '保持热爱，奔赴山海',
  '每一步都算数',
  '坚持是最美的姿态',
]

export async function getProjectStats(projectId: string): Promise<ProjectStats> {
  const allTasks = await taskRepo.findAll('project_id = ? AND deleted_at IS NULL', [projectId])
  const passedTasks = allTasks.filter(t => t.status === 'passed')
  const score = await gamificationRepo.getScore(projectId)
  return {
    totalTasks: allTasks.length,
    passedTasks: passedTasks.length,
    completionRate: allTasks.length > 0 ? passedTasks.length / allTasks.length : 0,
    streakDays: score?.streak_days ?? 0,
    totalPoints: score?.total_points ?? 0,
  }
}

export async function getUserStats(): Promise<UserStats> {
  const totalPoints = await gamificationRepo.getTotalPoints()
  const maxStreakDays = await statsRepo.getMaxStreak()
  const knowledgeCount = await statsRepo.getKnowledgeCount()
  const completedTasks = await statsRepo.getCompletedTaskCount()
  const db = await taskRepo.db()
  const passedResult = await db.query(
    "SELECT COUNT(*) as cnt FROM tasks WHERE status = 'passed' AND deleted_at IS NULL"
  )
  const passedTasks = (passedResult.values?.[0]?.cnt as number) ?? 0
  return {
    totalPoints,
    maxStreakDays,
    knowledgeCount,
    totalSubmissions: completedTasks,
    passedSubmissions: passedTasks,
    approvalRate: completedTasks > 0 ? passedTasks / completedTasks : 0,
  }
}

export async function getDashboard(): Promise<DashboardData> {
  const todayStr = today()
  const todayTasks = await taskRepo.findAll(
    "deleted_at IS NULL AND due_date = ? AND status = 'pending'",
    [todayStr],
    'sort_order ASC'
  )
  const maxStreakDays = await statsRepo.getMaxStreak()
  const totalPoints = await gamificationRepo.getTotalPoints()
  const knowledgeCount = await statsRepo.getKnowledgeCount()
  const quote = DAILY_QUOTES[new Date().getDate() % DAILY_QUOTES.length]

  return {
    todayPendingCount: todayTasks.length,
    todayTasks: todayTasks as unknown as DashboardTask[],
    maxStreakDays,
    totalPoints,
    knowledgeCount,
    dailyQuote: quote,
  }
}

export async function getActivityHeatmap(): Promise<DailyActivity[]> {
  return statsRepo.getActivityHeatmap(90)
}
