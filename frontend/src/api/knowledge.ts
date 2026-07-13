/**
 * 知识库 API（离线版）— 接口签名不变，底层改为 SQLite
 */
import { knowledgeRepo, rowToEntity, type KnowledgeEntryRow } from '@/db/repositories'

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

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

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

function parseTags(tags: unknown): string[] | null {
  if (!tags || typeof tags !== 'string') return null
  try { return JSON.parse(tags) } catch { return null }
}

function rowToListItem(row: KnowledgeEntryRow): KnowledgeEntryListItem {
  return rowToEntity<KnowledgeEntryListItem>(row)
}

function rowToDetail(row: KnowledgeEntryRow): KnowledgeEntryDetail {
  const detail = rowToEntity<KnowledgeEntryDetail>(row)
  detail.tags = parseTags(row.tags)
  return detail
}

export async function listKnowledge(): Promise<KnowledgeEntryListItem[]> {
  const rows = await knowledgeRepo.listAll()
  return rows.map(rowToListItem)
}

export async function getKnowledge(id: string): Promise<KnowledgeEntryDetail> {
  const row = await knowledgeRepo.findById(id)
  if (!row) throw new Error('知识条目不存在')
  return rowToDetail(row)
}

export async function searchKnowledge(
  q: string,
  page = 0,
  size = 10
): Promise<PageResult<SearchResultItem>> {
  const rows = await knowledgeRepo.search(q)
  const start = page * size
  const list: SearchResultItem[] = rows.slice(start, start + size).map(row => ({
    id: row.id,
    title: row.title,
    summary: row.summary,
    highlightedSummary: row.summary ?? row.title,
    sourceType: row.source_type,
    tags: parseTags(row.tags),
    wordCount: row.word_count,
    createdAt: row.created_at ?? '',
  }))
  return {
    list,
    total: rows.length,
    page,
    size,
    totalPages: Math.ceil(rows.length / size),
  }
}

export async function deleteKnowledge(id: string): Promise<void> {
  await knowledgeRepo.softDelete(id)
}

/** 离线版：上传文件直接读文本内容存本地 */
export async function uploadKnowledge(file: File): Promise<UploadResponse> {
  const text = await file.text()
  const row = await knowledgeRepo.createEntry({
    title: file.name,
    content: text,
    contentMd: text,
    sourceType: 'upload',
    summary: text.substring(0, 200),
    tags: [],
    mimeType: file.type,
  })
  return {
    entryId: row.id,
    title: row.title,
    filePath: row.file_path ?? '',
    sourceType: row.source_type ?? 'upload',
    wordCount: row.word_count,
    tags: [],
    links: [],
    createdAt: row.created_at ?? '',
  }
}

export interface KnowledgeLinkItem {
  entryId: string | null
  title: string
  linkText: string
  exists: boolean
}

export interface KnowledgeLinksResponse {
  outgoing: KnowledgeLinkItem[]
  incoming: KnowledgeLinkItem[]
}

export async function getKnowledgeLinks(id: string): Promise<KnowledgeLinksResponse> {
  // 离线版暂不支持双向链接（可后续扩展）
  return { outgoing: [], incoming: [] }
}
