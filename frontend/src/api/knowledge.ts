import { request } from './request'

/** 知识条目列表项（不含正文） */
export interface KnowledgeEntryListItem {
  id: string
  title: string
  summary: string | null
  sourceType: string | null
  tags: string[] | null
  wordCount: number | null
  parseStatus: string | null
  createdAt: string
  updatedAt: string
}

/** 知识条目详情（含正文） */
export interface KnowledgeEntryDetail {
  id: string
  title: string
  summary: string | null
  content: string | null
  contentMd: string | null
  sourceType: string | null
  filePath: string | null
  mimeType: string | null
  tags: string[] | null
  wordCount: number | null
  parseStatus: string | null
  createdAt: string
  updatedAt: string
}

/** 搜索结果项（含高亮摘要） */
export interface SearchResultItem {
  id: string
  title: string
  summary: string | null
  highlightedSummary: string
  sourceType: string | null
  tags: string[] | null
  wordCount: number | null
  createdAt: string
}

/** 分页结果 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

/** 上传响应 */
export interface UploadResponse {
  entryId: string
  title: string
  filePath: string
  sourceType: string
  wordCount: number
  tags: string[]
  links: string[]
  createdAt: string
}

/** 列出所有知识条目 */
export function listKnowledge(): Promise<KnowledgeEntryListItem[]> {
  return request.get<KnowledgeEntryListItem[]>('/knowledge')
}

/** 获取知识条目详情 */
export function getKnowledge(id: string): Promise<KnowledgeEntryDetail> {
  return request.get<KnowledgeEntryDetail>(`/knowledge/${id}`)
}

/** 全文检索 */
export function searchKnowledge(
  q: string,
  page = 0,
  size = 10
): Promise<PageResult<SearchResultItem>> {
  return request.get<PageResult<SearchResultItem>>('/knowledge/search', {
    params: { q, page, size }
  })
}

/** 软删除知识条目 */
export function deleteKnowledge(id: string): Promise<void> {
  return request.delete<void>(`/knowledge/${id}`)
}

/** 上传文件到知识库（multipart） */
export function uploadKnowledge(file: File): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<UploadResponse>('/knowledge/upload', formData)
}

/** 双向链接项 */
export interface KnowledgeLinkItem {
  entryId: string | null
  title: string
  linkText: string
  exists: boolean
}

/** 双向链接响应（出链 + 反链） */
export interface KnowledgeLinksResponse {
  outgoing: KnowledgeLinkItem[]
  incoming: KnowledgeLinkItem[]
}

/** 查询知识条目的双向链接 */
export function getKnowledgeLinks(id: string): Promise<KnowledgeLinksResponse> {
  return request.get<KnowledgeLinksResponse>(`/knowledge/${id}/links`)
}
