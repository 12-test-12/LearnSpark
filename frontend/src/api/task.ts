import { request } from './request'

/** 任务信息 */
export interface TaskInfo {
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
  sortOrder: number
  createdAt: string
  updatedAt: string
}

/** 提交审核结果 */
export interface SubmissionResult {
  submissionId: string
  taskId: string
  content: string
  attachmentUrls: string[] | null
  passed: boolean
  score: number
  feedback: string
  aiModel: string
  taskStatus: string
  knowledgeEntryId: string
  submittedAt: string
  reviewedAt: string
}

/** 获取单个任务详情 */
export function getTask(taskId: string): Promise<TaskInfo> {
  return request.get<TaskInfo>(`/tasks/${taskId}`)
}

/** 提交任务并触发 AI 审核 */
export function submitTask(
  taskId: string,
  data: { content: string; attachmentUrls?: string[] }
): Promise<SubmissionResult> {
  return request.post<SubmissionResult>(`/tasks/${taskId}/submit`, data)
}
