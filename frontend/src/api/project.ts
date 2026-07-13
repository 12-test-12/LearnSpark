/**
 * ============================================================
 *  项目 / 阶段 / 任务 API（离线版）
 *
 *  改造前：调用后端 REST API
 *  改造后：调用本地 SQLite Repository
 *  接口签名完全不变，组件代码无需修改
 * ============================================================
 */

import { projectRepo, phaseRepo, taskRepo, rowToEntity, type ProjectRow, type PhaseRow, type TaskRow } from '@/db/repositories'

// ============ 类型定义（保持不变）============

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

export interface ProjectRequest {
  name: string
  description?: string
  goal?: string
  dailyHours?: number
  coverColor?: string
}

export interface Phase {
  id: string
  projectId: string
  name: string
  objective: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface PhaseRequest {
  projectId: string
  name: string
  objective?: string
  sortOrder?: number
}

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

// ============ 转换函数：数据库行 → 前端对象 ============

function rowToProject(row: ProjectRow): Project {
  return rowToEntity<Project>(row)
}

function rowToPhase(row: PhaseRow): Phase {
  return rowToEntity<Phase>(row)
}

function rowToTask(row: TaskRow): Task {
  return rowToEntity<Task>(row)
}

// ============ 项目 API ============

export async function listProjects(): Promise<Project[]> {
  const rows = await projectRepo.listActive()
  return rows.map(rowToProject)
}

export async function getProject(id: string): Promise<Project> {
  const row = await projectRepo.findById(id)
  if (!row) throw new Error('项目不存在')
  return rowToProject(row)
}

export async function createProject(data: ProjectRequest): Promise<Project> {
  const row = await projectRepo.createProject(data)
  return rowToProject(row)
}

export async function updateProject(id: string, data: ProjectRequest): Promise<Project> {
  await projectRepo.updateProject(id, data)
  const row = await projectRepo.findById(id)
  if (!row) throw new Error('项目不存在')
  return rowToProject(row)
}

export async function deleteProject(id: string): Promise<void> {
  await projectRepo.softDeleteProject(id)
}

// ============ 阶段 API ============

export async function listPhases(projectId: string): Promise<Phase[]> {
  const rows = await phaseRepo.listByProject(projectId)
  return rows.map(rowToPhase)
}

export async function createPhase(data: PhaseRequest): Promise<Phase> {
  const row = await phaseRepo.createPhase(data)
  return rowToPhase(row)
}

export async function updatePhase(id: string, data: PhaseRequest): Promise<Phase> {
  const updateData: Record<string, unknown> = {
    project_id: data.projectId,
    name: data.name,
    objective: data.objective ?? null,
    sort_order: data.sortOrder ?? 0,
  }
  await phaseRepo.update(id, updateData as Partial<PhaseRow>)
  const row = await phaseRepo.findById(id)
  if (!row) throw new Error('阶段不存在')
  return rowToPhase(row)
}

export async function deletePhase(id: string): Promise<void> {
  await phaseRepo.delete(id)
}

// ============ 任务 API ============

export async function listTasks(projectId: string, date?: string): Promise<Task[]> {
  const rows = await taskRepo.listByProject(projectId, date)
  return rows.map(rowToTask)
}

export async function getTask(id: string): Promise<Task> {
  const row = await taskRepo.findById(id)
  if (!row) throw new Error('任务不存在')
  return rowToTask(row)
}

export async function createTask(data: TaskRequest): Promise<Task> {
  const row = await taskRepo.createTask(data)
  return rowToTask(row)
}

export async function updateTask(id: string, data: TaskRequest): Promise<Task> {
  const updateData: Record<string, unknown> = {
    project_id: data.projectId,
    phase_id: data.phaseId ?? null,
    day_number: data.dayNumber ?? null,
    title: data.title ?? null,
    description: data.description,
    verification_criteria: data.verificationCriteria ?? null,
    due_date: data.dueDate ?? null,
    sort_order: data.sortOrder ?? 0,
  }
  await taskRepo.update(id, updateData as Partial<TaskRow>)
  const row = await taskRepo.findById(id)
  if (!row) throw new Error('任务不存在')
  return rowToTask(row)
}

export async function deleteTask(id: string): Promise<void> {
  await taskRepo.softDelete(id)
}
