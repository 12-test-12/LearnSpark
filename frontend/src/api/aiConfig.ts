/**
 * AI 配置 API（离线版）— 接口签名不变，底层改为 SQLite
 * API Key 直接明文存本地（离线单用户，无需加密）
 */
import { aiConfigRepo } from '@/db/repositories'

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

export interface AiConfigRequest {
  deepseekApiKey?: string
  searchApiKey?: string
  deepseekBaseUrl?: string
  deepseekModel?: string
  searchProvider?: string
  localMode?: boolean
  embeddingModel?: string
}

export interface AiConfigTestRequest {
  provider: 'deepseek' | 'search'
  apiKey?: string
  baseUrl?: string
}

export interface AiConfigTestResponse {
  success: boolean
  message: string
  latencyMs: number
}

export async function getAiConfig(): Promise<AiConfigResponse> {
  const config = await aiConfigRepo.getConfig()
  return {
    deepseekApiKey: config?.deepseek_api_key ?? '',
    hasDeepseekKey: !!config?.deepseek_api_key,
    searchApiKey: config?.search_api_key ?? '',
    hasSearchKey: !!config?.search_api_key,
    deepseekBaseUrl: config?.deepseek_base_url ?? 'https://api.deepseek.com/v1',
    deepseekModel: config?.deepseek_model ?? 'deepseek-chat',
    searchProvider: config?.search_provider ?? 'bing',
    localMode: !!config?.local_mode,
    embeddingModel: config?.embedding_model ?? 'bge-large-zh',
  }
}

export async function saveAiConfig(data: AiConfigRequest): Promise<AiConfigResponse> {
  await aiConfigRepo.saveConfig({
    apiKey: data.deepseekApiKey,
    baseUrl: data.deepseekBaseUrl,
    model: data.deepseekModel,
    searchApiKey: data.searchApiKey,
    searchProvider: data.searchProvider,
  })
  return getAiConfig()
}

/** 测试 AI 配置连通性（前端直调 DeepSeek API） */
export async function testAiConfig(data: AiConfigTestRequest): Promise<AiConfigTestResponse> {
  const start = Date.now()
  try {
    if (data.provider === 'deepseek') {
      const baseUrl = data.baseUrl || 'https://api.deepseek.com/v1'
      const resp = await fetch(`${baseUrl}/models`, {
        headers: { Authorization: `Bearer ${data.apiKey}` },
      })
      if (resp.ok) {
        return { success: true, message: '连接成功', latencyMs: Date.now() - start }
      }
      return { success: false, message: `HTTP ${resp.status}`, latencyMs: Date.now() - start }
    }
    return { success: true, message: '搜索 API 测试跳过（离线模式）', latencyMs: 0 }
  } catch (e) {
    return { success: false, message: (e as Error).message, latencyMs: Date.now() - start }
  }
}
