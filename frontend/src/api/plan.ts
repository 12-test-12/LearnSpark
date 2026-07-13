/**
 * AI 生成学习计划 API（离线版）— 接口签名不变
 * 前端直调 DeepSeek API 生成计划
 */
import { phaseRepo, taskRepo, projectRepo, type PhaseRow, type TaskRow } from '@/db/repositories'
import { generatePlanWithAI, type GeneratedPhase } from '@/db/deepseek-client'
import type { Phase, Task } from './project'

export interface GeneratePlanResult {
  projectId: string
  phases: Phase[]
  tasks: Task[]
  phaseCount: number
  taskCount: number
}

/**
 * AI 生成学习路线（离线版）
 */
export async function generatePlan(
  projectId: string,
  files: File[] = [],
  useWebSearch = false,
  targetDays?: number,
  replaceMode = true
): Promise<GeneratePlanResult> {
  // 1. 获取项目信息
  const project = await projectRepo.findById(projectId)
  if (!project) throw new Error('项目不存在')

  // 2. 如果 replaceMode，先删除已有阶段和任务
  if (replaceMode) {
    const db = await taskRepo.db()
    // 先删任务再删阶段（外键约束）
    await db.run('DELETE FROM tasks WHERE project_id = ?', [projectId])
    await db.run('DELETE FROM phases WHERE project_id = ?', [projectId])
  }

  // 3. 读取上传文件内容
  const fileContents: string[] = []
  for (const file of files) {
    try {
      const text = await file.text()
      fileContents.push(text)
    } catch {
      // 跳过无法读取的文件
    }
  }

  // 4. 调用 AI 生成计划
  const aiResult = await generatePlanWithAI({
    projectName: project.name,
    projectGoal: project.goal ?? undefined,
    dailyHours: project.daily_hours,
    targetDays,
    fileContents,
    useWebSearch,
  })

  // 5. 存入数据库
  const phases: Phase[] = []
  const tasks: Task[] = []

  for (let i = 0; i < aiResult.phases.length; i++) {
    const genPhase: GeneratedPhase = aiResult.phases[i]
    const phaseRow = await phaseRepo.createPhase({
      projectId,
      name: genPhase.name,
      objective: genPhase.objective,
      sortOrder: i,
    })
    phases.push(rowToPhase(phaseRow))

    for (let j = 0; j < genPhase.tasks.length; j++) {
      const genTask = genPhase.tasks[j]
      const taskRow = await taskRepo.createTask({
        projectId,
        phaseId: phaseRow.id,
        dayNumber: genTask.dayNumber ?? null,
        title: genTask.title,
        description: genTask.description,
        verificationCriteria: genTask.verificationCriteria,
        sortOrder: j,
      })
      tasks.push(rowToTask(taskRow))
    }
  }

  // 6. 标记项目为 AI 生成
  await projectRepo.update(projectId, { is_ai_generated: 1 } as Partial<ProjectRow>)

  return {
    projectId,
    phases,
    tasks,
    phaseCount: phases.length,
    taskCount: tasks.length,
  }
}

function rowToPhase(row: PhaseRow): Phase {
  return {
    id: row.id,
    projectId: row.project_id,
    name: row.name,
    objective: row.objective,
    sortOrder: row.sort_order,
    createdAt: row.created_at ?? '',
    updatedAt: row.updated_at ?? '',
  }
}

function rowToTask(row: TaskRow): Task {
  return {
    id: row.id,
    phaseId: row.phase_id,
    projectId: row.project_id,
    dayNumber: row.day_number,
    title: row.title,
    description: row.description,
    verificationCriteria: row.verification_criteria,
    status: row.status as Task['status'],
    dueDate: row.due_date,
    completedAt: row.completed_at,
    sortOrder: row.sort_order,
    createdAt: row.created_at ?? '',
    updatedAt: row.updated_at ?? '',
  }
}
