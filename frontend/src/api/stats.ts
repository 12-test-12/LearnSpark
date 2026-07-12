import { request } from './request'

/** 项目统计 */
export interface ProjectStats {
  totalTasks: number
  passedTasks: number
  completionRate: number
  streakDays: number
  totalPoints: number
}

/** 用户全局统计 */
export interface UserStats {
  totalPoints: number
  maxStreakDays: number
  knowledgeCount: number
  totalSubmissions: number
  passedSubmissions: number
  approvalRate: number
}

/** 仪表盘今日任务项（复用 TaskResponse 结构） */
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

/** 仪表盘聚合数据 */
export interface DashboardData {
  todayPendingCount: number
  todayTasks: DashboardTask[]
  maxStreakDays: number
  totalPoints: number
  knowledgeCount: number
  dailyQuote: string
}

/** 获取项目统计 */
export function getProjectStats(projectId: string): Promise<ProjectStats> {
  return request.get<ProjectStats>(`/projects/${projectId}/stats`)
}

/** 获取用户全局统计 */
export function getUserStats(): Promise<UserStats> {
  return request.get<UserStats>('/user/stats')
}

/** 获取仪表盘聚合数据 */
export function getDashboard(): Promise<DashboardData> {
  return request.get<DashboardData>('/user/dashboard')
}

/** 每日活动统计项（热力图数据） */
export interface DailyActivity {
  date: string
  count: number
}

/** 获取用户近 90 天每日活动统计（热力图数据） */
export function getActivityHeatmap(): Promise<DailyActivity[]> {
  return request.get<DailyActivity[]>('/user/activity')
}
