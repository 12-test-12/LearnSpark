/**
 * 任务 API（离线版）— 接口签名不变
 * 提交任务后前端直调 DeepSeek 进行 AI 审核
 */
import { taskRepo, submissionRepo, gamificationRepo, knowledgeRepo, rowToEntity, type TaskRow } from '@/db/repositories'
import { reviewTaskWithAI } from '@/db/deepseek-client'

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

export async function getTask(taskId: string): Promise<TaskInfo> {
  const row = await taskRepo.findById(taskId)
  if (!row) throw new Error('任务不存在')
  return rowToEntity<TaskInfo>(row)
}

/**
 * 提交任务并触发 AI 审核
 * 离线版流程：
 *   1. 创建 submission 记录
 *   2. 调用 DeepSeek API 审核
 *   3. 更新 submission 的 AI 结果
 *   4. 更新 task 状态
 *   5. 增加积分 + 打卡
 *   6. 通过的提交自动归入知识库
 */
export async function submitTask(
  taskId: string,
  data: { content: string; attachmentUrls?: string[] }
): Promise<SubmissionResult> {
  // 1. 查任务
  const task = await taskRepo.findById(taskId)
  if (!task) throw new Error('任务不存在')

  // 2. 创建提交记录
  const submission = await submissionRepo.createSubmission({
    taskId,
    content: data.content,
    attachmentUrls: data.attachmentUrls,
  })

  // 3. AI 审核（如果配置了 API Key）
  let aiResult
  let aiModel = 'local'
  try {
    aiResult = await reviewTaskWithAI({
      taskTitle: task.title ?? task.description,
      taskDescription: task.description,
      verificationCriteria: task.verification_criteria ?? '',
      submissionContent: data.content,
    })
    aiModel = 'deepseek'
  } catch (e) {
    // 没有配置 API Key 或调用失败时，自动通过
    aiResult = {
      passed: true,
      score: 7,
      feedback: `自动通过（AI 审核不可用：${(e as Error).message}）`,
    }
  }

  // 4. 更新提交记录
  await submissionRepo.updateAiResult(submission.id, {
    feedback: aiResult.feedback,
    score: aiResult.score,
    passed: aiResult.passed,
    model: aiModel,
  })

  // 5. 更新任务状态
  const newStatus = aiResult.passed ? 'passed' : 'failed'
  await taskRepo.updateStatus(taskId, newStatus)

  // 6. 积分 + 打卡
  if (aiResult.passed) {
    await gamificationRepo.addPoints(task.project_id, aiResult.score * 10)
  }

  // 7. 通过的提交归入知识库
  let knowledgeEntryId = ''
  if (aiResult.passed) {
    const entry = await knowledgeRepo.createEntry({
      projectId: task.project_id,
      title: task.title ?? '未命名任务',
      content: data.content,
      contentMd: data.content,
      summary: data.content.substring(0, 200),
      sourceType: 'submission',
      sourceId: submission.id,
    })
    knowledgeEntryId = entry.id
  }

  // 8. 检查并颁发徽章
  await checkAndAwardBadges(task.project_id)

  return {
    submissionId: submission.id,
    taskId,
    content: data.content,
    attachmentUrls: data.attachmentUrls ?? null,
    passed: aiResult.passed,
    score: aiResult.score,
    feedback: aiResult.feedback,
    aiModel,
    taskStatus: newStatus,
    knowledgeEntryId,
    submittedAt: submission.submitted_at ?? '',
    reviewedAt: new Date().toISOString().replace('T', ' ').substring(0, 19),
  }
}

/** 检查并颁发徽章 */
async function checkAndAwardBadges(projectId: string): Promise<void> {
  const allBadges = await gamificationRepo.listBadges()
  const userBadges = await gamificationRepo.listUserBadges()
  const awardedCodes = new Set(userBadges.map(b => b.code))

  for (const badge of allBadges) {
    if (awardedCodes.has(badge.code)) continue

    let shouldAward = false
    const score = await gamificationRepo.getScore(projectId)

    switch (badge.rule_type) {
      case 'count':
        if (badge.code === 'first_pass') {
          // 第一次通过（有 passed 状态的任务）
          const db = await taskRepo.db()
          const result = await db.query(
            "SELECT COUNT(*) as cnt FROM tasks WHERE status = 'passed' AND deleted_at IS NULL"
          )
          if ((result.values?.[0]?.cnt as number) >= 1) shouldAward = true
        }
        if (badge.code === 'kb_10') {
          const count = await knowledgeRepo.count('user_id = ?', ['local-user'])
          if (count >= 10) shouldAward = true
        }
        break
      case 'score':
        if (score && score.total_points >= badge.rule_value) shouldAward = true
        break
      case 'streak':
        if (badge.code === 'streak_7' && score && score.streak_days >= 7) shouldAward = true
        if (badge.code === 'streak_30' && score && score.streak_days >= 30) shouldAward = true
        break
    }

    if (shouldAward) {
      await gamificationRepo.awardBadge(badge.id)
    }
  }
}
