<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NCard, NSpace, NText, NTag, NButton, NIcon, NSpin, NResult,
  NEmpty, NTime
} from 'naive-ui'
import {
  ArrowBackOutline, DocumentTextOutline, LinkOutline,
  ArrowForwardOutline
} from '@vicons/ionicons5'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  getKnowledge, getKnowledgeLinks,
  type KnowledgeEntryDetail, type KnowledgeLinksResponse, type KnowledgeLinkItem
} from '@/api/knowledge'
import { useThemeStore } from '@/stores/theme'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()

const loading = ref(true)
const loadError = ref('')
const entry = ref<KnowledgeEntryDetail | null>(null)
const links = ref<KnowledgeLinksResponse | null>(null)

/** 出链文本 → 链接项映射，供正文 wikilink 解析复用（SSOT） */
const outgoingMap = computed(() => {
  const map = new Map<string, KnowledgeLinkItem>()
  links.value?.outgoing.forEach((item) => map.set(item.linkText, item))
  return map
})

/** 反链列表（其他笔记引用了本篇） */
const incomingLinks = computed(() => links.value?.incoming ?? [])

/** 出链列表（本篇引用的其他笔记） */
const outgoingLinks = computed(() => links.value?.outgoing ?? [])

/**
 * 渲染 Markdown：先将 [[wikilink]] 替换为可交互元素，再交给 marked + DOMPurify。
 * 已匹配目标 → 可跳转链接；未匹配 → 「待创建」灰显标记。
 */
const renderedContent = computed(() => {
  if (!entry.value) return ''
  const md = entry.value.contentMd || entry.value.content || ''
  if (!md) return ''
  const withLinks = md.replace(/\[\[([^\]]+)\]\]/g, (_, text) => buildWikilinkHtml(text))
  const rawHtml = marked.parse(withLinks, { async: false }) as string
  return DOMPurify.sanitize(rawHtml, { ADD_ATTR: ['data-entry-id', 'data-link'] })
})

/** 构建单个 wikilink 的 HTML：已匹配→链接，未匹配→待创建标记 */
function buildWikilinkHtml(text: string): string {
  const item = outgoingMap.value.get(text.trim())
  if (item?.entryId) {
    return `<a class="wikilink wl-exists" data-entry-id="${item.entryId}">${text}</a>`
  }
  return `<span class="wikilink wl-missing" data-link="${text}">${text}<small>待创建</small></span>`
}

/** 并行加载笔记详情与双向链接 */
async function loadEntry() {
  loading.value = true
  loadError.value = ''
  try {
    const id = route.params.id as string
    const [entryData, linksData] = await Promise.all([
      getKnowledge(id),
      getKnowledgeLinks(id)
    ])
    entry.value = entryData
    links.value = linksData
  } catch (e: unknown) {
    loadError.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push({ name: 'knowledge' })
}

/** 跳转到目标笔记详情 */
function goToEntry(id: string) {
  router.push({ name: 'knowledge-detail', params: { id } })
}

/** 未匹配出链 → 跳到知识库搜索，便于查找或新建 */
function searchLinkText(text: string) {
  router.push({ name: 'knowledge', query: { q: text } })
}

/** 处理正文区域内 wikilink 点击事件（事件委托） */
function handleContentClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  const wikilink = target.closest('.wikilink') as HTMLElement | null
  if (!wikilink) return
  const entryId = wikilink.getAttribute('data-entry-id')
  if (entryId) {
    goToEntry(entryId)
  } else {
    const text = wikilink.getAttribute('data-link') || wikilink.textContent || ''
    searchLinkText(text)
  }
}

/** 来源类型中文标签 */
function sourceLabel(source: string | null): string {
  const map: Record<string, string> = {
    upload: '上传',
    submission: '任务提交',
    manual: '手动创建'
  }
  return source ? (map[source] || source) : ''
}

onMounted(loadEntry)

// 路由参数变化时重新加载（点击 wikilink 跳转到另一篇笔记时触发）
watch(() => route.params.id, (newId, oldId) => {
  if (newId && newId !== oldId) loadEntry()
})
</script>

<template>
  <div class="detail-page">
    <!-- 顶部导航 -->
    <n-space justify="space-between" align="center" style="margin-bottom: 16px">
      <n-button quaternary @click="goBack">
        <template #icon><n-icon :component="ArrowBackOutline" /></template>
        返回知识库
      </n-button>
    </n-space>

    <!-- 加载中 -->
    <n-spin v-if="loading" size="large" style="display: flex; justify-content: center; padding: 80px 0" />

    <!-- 加载失败 -->
    <n-result v-else-if="loadError" status="error" title="加载失败" :description="loadError">
      <template #footer>
        <n-button @click="goBack">返回知识库</n-button>
      </template>
    </n-result>

    <!-- 内容 -->
    <template v-else-if="entry">
      <div class="detail-grid">
        <!-- 主内容区 -->
        <div class="detail-main">
          <n-card :bordered="false" size="medium">
            <template #header>
              <n-space align="center" :size="8">
                <n-icon :component="DocumentTextOutline" color="#18a058" :size="20" />
                <span class="detail-title">{{ entry.title }}</span>
              </n-space>
            </template>
            <template #header-extra>
              <n-space align="center" :size="12">
                <n-tag size="small" round>{{ sourceLabel(entry.sourceType) }}</n-tag>
                <n-text depth="3" style="font-size: 12px">{{ entry.wordCount || 0 }} 字</n-text>
                <n-text depth="3" style="font-size: 12px">
                  <n-time :time="new Date(entry.createdAt)" />
                </n-text>
              </n-space>
            </template>

            <!-- Markdown 正文渲染区 -->
            <div
              v-if="renderedContent"
              :class="['markdown-body', { 'markdown-body--dark': themeStore.isDark }]"
              v-html="renderedContent"
              @click="handleContentClick"
            ></div>
            <n-empty v-else description="暂无正文内容" style="padding: 40px 0" />
          </n-card>
        </div>

        <!-- 侧边栏：双向链接 -->
        <div class="detail-sidebar">
          <!-- 反向链接 -->
          <n-card :bordered="false" size="small" class="link-card">
            <template #header>
              <n-space align="center" :size="6">
                <n-icon :component="LinkOutline" color="#18a058" :size="16" />
                <span>反向链接</span>
                <n-tag size="tiny" round :bordered="false">{{ incomingLinks.length }}</n-tag>
              </n-space>
            </template>
            <n-empty v-if="incomingLinks.length === 0" description="暂无其他笔记引用本篇" size="small" />
            <div v-else class="link-list">
              <div
                v-for="item in incomingLinks"
                :key="item.entryId"
                class="link-item"
                @click="goToEntry(item.entryId!)"
              >
                <n-icon :component="ArrowForwardOutline" :size="14" color="#999" />
                <span class="link-title">{{ item.title }}</span>
              </div>
            </div>
          </n-card>

          <!-- 出链列表 -->
          <n-card :bordered="false" size="small" class="link-card">
            <template #header>
              <n-space align="center" :size="6">
                <n-icon :component="LinkOutline" color="#18a058" :size="16" />
                <span>出链</span>
                <n-tag size="tiny" round :bordered="false">{{ outgoingLinks.length }}</n-tag>
              </n-space>
            </template>
            <n-empty v-if="outgoingLinks.length === 0" description="本篇未引用其他笔记" size="small" />
            <div v-else class="link-list">
              <div
                v-for="item in outgoingLinks"
                :key="item.linkText"
                class="link-item"
                :class="{ 'link-item--missing': !item.entryId }"
                @click="item.entryId ? goToEntry(item.entryId) : searchLinkText(item.title)"
              >
                <n-icon :component="ArrowForwardOutline" :size="14" color="#999" />
                <span class="link-title">{{ item.title }}</span>
                <n-tag v-if="!item.entryId" size="tiny" type="warning" round :bordered="false">待创建</n-tag>
              </div>
            </div>
          </n-card>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.detail-page {
  max-width: 1000px;
  margin: 0 auto;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

@media (min-width: 1024px) {
  .detail-grid {
    grid-template-columns: 1fr 280px;
  }
  .detail-sidebar {
    grid-column: 2;
  }
}

.detail-title {
  font-weight: 700;
  font-size: 18px;
}

/* 侧边栏链接卡片 */
.link-card {
  margin-bottom: 12px;
}

.link-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.link-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: rgba(24, 160, 88, 0.08);
  }

  .link-title {
    flex: 1;
    font-size: 13px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.link-item--missing {
  opacity: 0.6;
}

/* Markdown 正文：通过 CSS 变量实现深浅模式 SSOT */
.markdown-body {
  --md-text: #333;
  --md-border: #e0e0e0;
  --md-quote: #666;
  --md-code-bg: rgba(128, 128, 128, 0.1);
  --md-pre-bg: #1e1e1e;
  --md-pre-text: #d4d4d4;
  --md-table-th-bg: rgba(128, 128, 128, 0.05);
  --md-wl-bg: rgba(24, 160, 88, 0.08);
  --md-wl-bg-hover: rgba(24, 160, 88, 0.15);

  font-size: 14px;
  line-height: 1.8;
  color: var(--md-text);

  :deep(h1), :deep(h2), :deep(h3), :deep(h4), :deep(h5), :deep(h6) {
    margin-top: 1.5em;
    margin-bottom: 0.5em;
    font-weight: 600;
  }
  :deep(h1) { font-size: 1.8em; }
  :deep(h2) { font-size: 1.5em; }
  :deep(h3) { font-size: 1.25em; }
  :deep(h4) { font-size: 1.1em; }

  :deep(p) { margin: 0.8em 0; }

  :deep(code) {
    background: var(--md-code-bg);
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 0.9em;
  }

  :deep(pre) {
    background: var(--md-pre-bg);
    color: var(--md-pre-text);
    padding: 16px;
    border-radius: 8px;
    overflow-x: auto;
    margin: 1em 0;

    code {
      background: none;
      padding: 0;
      color: inherit;
    }
  }

  :deep(blockquote) {
    border-left: 4px solid #18a058;
    padding-left: 16px;
    margin: 1em 0;
    color: var(--md-quote);
  }

  :deep(ul), :deep(ol) {
    padding-left: 2em;
    margin: 0.8em 0;
  }
  :deep(li) { margin: 0.4em 0; }

  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 1em 0;

    th, td {
      border: 1px solid var(--md-border);
      padding: 8px 12px;
      text-align: left;
    }
    th {
      background: var(--md-table-th-bg);
      font-weight: 600;
    }
  }

  :deep(a) {
    color: #18a058;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }

  /* wikilink 通用样式 */
  :deep(.wikilink) {
    color: #18a058;
    background: var(--md-wl-bg);
    padding: 1px 6px;
    border-radius: 4px;
    cursor: pointer;
    font-weight: 500;
    text-decoration: none;

    &:hover {
      background: var(--md-wl-bg-hover);
      text-decoration: none;
    }
  }

  /* 已匹配的 wikilink：可跳转 */
  :deep(.wl-exists) {
    cursor: pointer;
  }

  /* 未匹配的 wikilink：灰显 + 待创建标记 */
  :deep(.wl-missing) {
    color: var(--md-quote);
    cursor: pointer;

    small {
      font-size: 10px;
      margin-left: 4px;
      opacity: 0.7;
      font-weight: 400;
    }
  }
}

/* 深色模式：仅覆盖 CSS 变量，样式逻辑保持 SSOT */
.markdown-body--dark {
  --md-text: #e0e0e0;
  --md-border: #3a3a3a;
  --md-quote: #999;
  --md-code-bg: rgba(255, 255, 255, 0.08);
  --md-pre-bg: #0d0d0f;
  --md-pre-text: #e0e0e0;
  --md-table-th-bg: rgba(255, 255, 255, 0.05);
  --md-wl-bg: rgba(24, 160, 88, 0.12);
  --md-wl-bg-hover: rgba(24, 160, 88, 0.2);
}
</style>
