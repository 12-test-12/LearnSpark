import { request } from './request'

/** AI 配置响应（Key 已脱敏） */
export interface AiConfigResponse {
  deepseekApiKey: string
  hasDeepseekKey: boolean
  searchApiKey: string
  hasSearchKey: boolean
  deepseekBaseUrl: string
  deepseekModel: string
  searchProvider: string
  localMode: boolean
  embeddingModel: string
}

/** AI 配置更新请求（Key 为空时保留已有值；localMode=true 时清空 Key） */
export interface AiConfigRequest {
  deepseekApiKey?: string
  searchApiKey?: string
  deepseekBaseUrl?: string
  deepseekModel?: string
  searchProvider?: string
  localMode?: boolean
  embeddingModel?: string
}

/** 配置测试请求 */
export interface AiConfigTestRequest {
  provider: 'deepseek' | 'search'
  apiKey?: string
  baseUrl?: string
}

/** 配置测试响应 */
export interface AiConfigTestResponse {
  success: boolean
  message: string
  latencyMs: number
}

/** 获取当前用户 AI 配置 */
export function getAiConfig(): Promise<AiConfigResponse> {
  return request.get<AiConfigResponse>('/user/ai-config')
}

/** 更新当前用户 AI 配置 */
export function saveAiConfig(data: AiConfigRequest): Promise<AiConfigResponse> {
  return request.put<AiConfigResponse>('/user/ai-config', data)
}

/** 测试 AI 配置连通性 */
export function testAiConfig(data: AiConfigTestRequest): Promise<AiConfigTestResponse> {
  return request.post<AiConfigTestResponse>('/user/ai-config/test', data)
}
