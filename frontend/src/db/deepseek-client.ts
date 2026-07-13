/**
 * ============================================================
 *  LearnSpark · DeepSeek 客户端（前端直调）
 *
 *  离线模式下，AI 功能直接在前端调用 DeepSeek API
 *  需要用户在设置里配置 API Key
 *
 *  功能：
 *    1. generatePlan  — 生成学习计划
 *    2. reviewTask    — 审核任务提交
 * ============================================================
 */

import { aiConfigRepo } from '@/db/repositories'

interface DeepSeekMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}

interface DeepSeekResponse {
  choices: { message: { content: string } }[]
  usage?: { total_tokens: number }
  model?: string
}

/** 获取配置好的 DeepSeek 客户端参数 */
async function getDeepSeekConfig() {
  const config = await aiConfigRepo.getConfig()
  if (!config?.deepseek_api_key) {
    throw new Error('请先在设置中配置 DeepSeek API Key')
  }
  return {
    apiKey: config.deepseek_api_key,
    baseUrl: config.deepseek_base_url || 'https://api.deepseek.com/v1',
    model: config.deepseek_model || 'deepseek-chat',
  }
}

/** 调用 DeepSeek Chat API */
async function chatCompletion(messages: DeepSeekMessage[], options?: {
  temperature?: number
  maxTokens?: number
}): Promise<{ content: string; model: string; raw: DeepSeekResponse }> {
  const { apiKey, baseUrl, model } = await getDeepSeekConfig()

  const resp = await fetch(`${baseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model,
      messages,
      temperature: options?.temperature ?? 0.7,
      max_tokens: options?.maxTokens ?? 4096,
      stream: false,
    }),
  })

  if (!resp.ok) {
    const errText = await resp.text()
    throw new Error(`DeepSeek API 错误 (${resp.status}): ${errText}`)
  }

  const data: DeepSeekResponse = await resp.json()
  return {
    content: data.choices[0]?.message?.content ?? '',
    model: data.model ?? model,
    raw: data,
  }
}

// ============================================================
// AI 生成学习计划
// ============================================================

export interface GeneratedPhase {
  name: string
  objective: string
  tasks: GeneratedTask[]
}

export interface GeneratedTask {
  title: string
  description: string
  verificationCriteria: string
  dayNumber?: number
}

/** AI 生成学习计划 */
export async function generatePlanWithAI(params: {
  projectName: string
  projectGoal?: string
  dailyHours?: number
  targetDays?: number
  fileContents?: string[]
  useWebSearch?: boolean
}): Promise<{ phases: GeneratedPhase[] }> {
  const { projectName, projectGoal, dailyHours, targetDays, fileContents } = params

  const systemPrompt = `你是一位专业的学习规划师。根据用户的学习目标和资料，生成结构化的学习计划。

要求：
1. 按学习逻辑分阶段（phases），每个阶段有明确目标
2. 每个阶段包含多个具体任务（tasks）
3. 每个任务包含：标题、描述、验收标准
4. 如果指定了目标天数，按天数分配任务
5. 严格输出 JSON 格式`

  const userPrompt = `项目名称：${projectName}
学习目标：${projectGoal || '掌握相关知识和技能'}
每日学习时长：${dailyHours || 2} 小时
${targetDays ? `目标天数：${targetDays} 天` : ''}
${fileContents && fileContents.length > 0 ? `参考资料：\n${fileContents.join('\n---\n').substring(0, 8000)}` : ''}

请生成学习计划，严格按以下 JSON 格式输出（不要包含 markdown 代码块标记）：
{
  "phases": [
    {
      "name": "阶段名称",
      "objective": "阶段目标",
      "tasks": [
        {
          "title": "任务标题",
          "description": "详细描述要学什么、做什么",
          "verificationCriteria": "如何验证学好了",
          "dayNumber": 1
        }
      ]
    }
  ]
}`

  const result = await chatCompletion(
    [
      { role: 'system', content: systemPrompt },
      { role: 'user', content: userPrompt },
    ],
    { temperature: 0.7, maxTokens: 8192 }
  )

  // 解析 JSON（兼容 markdown 代码块包裹的情况）
  const jsonStr = result.content
    .replace(/^```json\s*/i, '')
    .replace(/^```\s*/i, '')
    .replace(/```\s*$/i, '')
    .trim()

  try {
    const parsed = JSON.parse(jsonStr)
    if (!parsed.phases || !Array.isArray(parsed.phases)) {
      throw new Error('AI 返回格式错误：缺少 phases 数组')
    }
    return { phases: parsed.phases }
  } catch (e) {
    throw new Error(`解析 AI 返回结果失败: ${(e as Error).message}`)
  }
}

// ============================================================
// AI 审核任务提交
// ============================================================

export interface TaskReviewResult {
  passed: boolean
  score: number
  feedback: string
}

/** AI 审核任务提交 */
export async function reviewTaskWithAI(params: {
  taskTitle: string
  taskDescription: string
  verificationCriteria: string
  submissionContent: string
}): Promise<TaskReviewResult> {
  const { taskTitle, taskDescription, verificationCriteria, submissionContent } = params

  const systemPrompt = `你是一位严格但鼓励学习的导师。根据任务要求和学员提交的内容，评估学员是否达标。

评分规则：
- 分数 1-10，7 分及以上为通过（passed=true）
- 给出具体的反馈，指出优点和不足
- 严格输出 JSON 格式`

  const userPrompt = `任务标题：${taskTitle}
任务描述：${taskDescription}
验收标准：${verificationCriteria || '理解并能复述相关概念'}

学员提交内容：
${submissionContent}

请评估并严格按以下 JSON 格式输出（不要包含 markdown 代码块标记）：
{
  "passed": true/false,
  "score": 8,
  "feedback": "具体的评估反馈..."
}`

  const result = await chatCompletion(
    [
      { role: 'system', content: systemPrompt },
      { role: 'user', content: userPrompt },
    ],
    { temperature: 0.3, maxTokens: 2048 }
  )

  const jsonStr = result.content
    .replace(/^```json\s*/i, '')
    .replace(/^```\s*/i, '')
    .replace(/```\s*$/i, '')
    .trim()

  try {
    const parsed = JSON.parse(jsonStr)
    return {
      passed: !!parsed.passed,
      score: Math.max(1, Math.min(10, Number(parsed.score) || 5)),
      feedback: parsed.feedback || '审核完成',
    }
  } catch (e) {
    // 解析失败时降级为通过
    return {
      passed: true,
      score: 7,
      feedback: `AI 审核完成，但返回格式解析失败。原始反馈：${result.content.substring(0, 500)}`,
    }
  }
}
