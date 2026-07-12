import { request } from './request'

// ============ 类型定义 ============

/** 项目 */
export interface Project {
  id: string
  name: string
  description: string | null
  goal: string | null
  dailyHours: number | null
  isAiGenerated: boolean
  status: string
  coverColor: string | null
  createdAt: string
  updatedAt: string
}

/** 项目创建/更新请求 */
export interface ProjectRequest {
  name: string
  description?: string
  goal?: string
  dailyHours?: number
  coverColor?: string
}

/** 阶段 */
export interface Phase {
  id: string
  projectId: string
  name: string
  objective: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

/** 阶段创建/更新请求 */
export interface PhaseRequest {
  projectId: string
  name: string
  objective?: string
  sortOrder?: number
}

/** 任务 */
export interface Task {
  id: string
  phaseId: string | null
  projectId: string
  dayNumber: number | null
  title: string | null
  description: string
  verificationCriteria: string | null
  status: TaskStatus
  dueDate: string | null
  completedAt: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export type TaskStatus = 'pending' | 'submitted' | 'passed' | 'failed'

/** 任务创建/更新请求 */
export interface TaskRequest {
  projectId: string
  phaseId?: string | null
  dayNumber?: number | null
  title?: string
  description: string
  verificationCriteria?: string
  dueDate?: string | null
  sortOrder?: number
}

// ============ 项目 API ============

export function listProjects(): Promise<Project[]> {
  return request.get<Project[]>('/projects')
}

export function getProject(id: string): Promise<Project> {
  return request.get<Project>(`/projects/${id}`)
}

export function createProject(data: ProjectRequest): Promise<Project> {
  return request.post<Project>('/projects', data)
}

export function updateProject(id: string, data: ProjectRequest): Promise<Project> {
  return request.put<Project>(`/projects/${id}`, data)
}

export function deleteProject(id: string): Promise<void> {
  return request.delete<void>(`/projects/${id}`)
}

// ============ 阶段 API ============

export function listPhases(projectId: string): Promise<Phase[]> {
  return request.get<Phase[]>(`/projects/${projectId}/phases`)
}

export function createPhase(data: PhaseRequest): Promise<Phase> {
  return request.post<Phase>('/phases', data)
}

export function updatePhase(id: string, data: PhaseRequest): Promise<Phase> {
  return request.put<Phase>(`/phases/${id}`, data)
}

export function deletePhase(id: string): Promise<void> {
  return request.delete<void>(`/phases/${id}`)
}

// ============ 任务 API ============

export function listTasks(projectId: string, date?: string): Promise<Task[]> {
  return request.get<Task[]>(`/projects/${projectId}/tasks`, { params: date ? { date } : undefined })
}

export function getTask(id: string): Promise<Task> {
  return request.get<Task>(`/tasks/${id}`)
}

export function createTask(data: TaskRequest): Promise<Task> {
  return request.post<Task>('/tasks', data)
}

export function updateTask(id: string, data: TaskRequest): Promise<Task> {
  return request.put<Task>(`/tasks/${id}`, data)
}

export function deleteTask(id: string): Promise<void> {
  return request.delete<void>(`/tasks/${id}`)
}
