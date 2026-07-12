import { request } from './request'
import type { Phase, Task } from './project'

/** AI 生成学习路线结果 */
export interface GeneratePlanResult {
  projectId: string
  phases: Phase[]
  tasks: Task[]
  phaseCount: number
  taskCount: number
}

/**
 * AI 生成学习路线。
 *
 * @param projectId    项目 ID
 * @param files        上传的资料文件（.md/.txt）
 * @param useWebSearch 是否启用联网搜索
 * @param targetDays   目标天数（可选）
 * @param replaceMode  是否替换已有阶段和任务
 * @returns 生成结果（含阶段和任务列表）
 */
export function generatePlan(
  projectId: string,
  files: File[] = [],
  useWebSearch = false,
  targetDays?: number,
  replaceMode = true
): Promise<GeneratePlanResult> {
  const formData = new FormData()
  files.forEach((f) => formData.append('files', f))
  formData.append('useWebSearch', String(useWebSearch))
  if (targetDays) formData.append('targetDays', String(targetDays))
  formData.append('replaceMode', String(replaceMode))
  return request.post<GeneratePlanResult>(
    `/projects/${projectId}/generate-plan`,
    formData,
    { timeout: 120000 }
  )
}
