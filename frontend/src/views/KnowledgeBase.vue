<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  NCard, NSpace, NInput, NButton, NTag, NEmpty, NSpin, NGrid, NGi,
  NText, NIcon, NPopconfirm, NUpload, NPagination, NDivider, NTime,
  NInputGroup
} from 'naive-ui'
import type { UploadCustomRequestOptions } from 'naive-ui'
import {
  SearchOutline, CloudUploadOutline, TrashOutline, TimeOutline,
  DocumentTextOutline, PricetagsOutline
} from '@vicons/ionicons5'
import {
  listKnowledge, searchKnowledge, deleteKnowledge, uploadKnowledge,
  type KnowledgeEntryListItem, type SearchResultItem
} from '@/api/knowledge'

const router = useRouter()
const route = useRoute()

// === 搜索状态 ===
const searchQuery = ref('')
const isSearching = ref(false)
const searchResults = ref<SearchResultItem[]>([])
const searchTotal = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// === 列表状态 ===
const entries = ref<KnowledgeEntryListItem[]>([])
const listLoading = ref(true)

// === 上传状态 ===
const uploading = ref(false)

// === 计算属性 ===

/** 所有标签及其出现次数（用于标签云） */
const tagCloud = computed(() => {
  const map = new Map<string, number>()
  for (const entry of entries.value) {
    if (entry.tags) {
      for (const tag of entry.tags) {
        map.set(tag, (map.get(tag) || 0) + 1)
      }
    }
  }
  return Array.from(map.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 20)
})

/** 最近编辑（按 updatedAt 降序，取前 5） */
const recentEntries = computed(() => {
  return [...entries.value]
    .sort((a, b) => (b.updatedAt || '').localeCompare(a.updatedAt || ''))
    .slice(0, 5)
})

/** 当前是否在搜索模式 */
const hasSearchQuery = computed(() => searchQuery.value.trim().length > 0)

// === 数据加载 ===

async function loadEntries() {
  listLoading.value = true
  try {
    entries.value = await listKnowledge()
  } catch {
    // 错误已由拦截器处理
  } finally {
    listLoading.value = false
  }
}

async function doSearch() {
  const q = searchQuery.value.trim()
  if (!q) {
    searchResults.value = []
    searchTotal.value = 0
    return
  }
  isSearching.value = true
  try {
    const result = await searchKnowledge(q, currentPage.value - 1, pageSize.value)
    searchResults.value = result.list
    searchTotal.value = result.total
  } catch {
    // 错误已由拦截器处理
  } finally {
    isSearching.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  doSearch()
}

function handlePageChange(page: number) {
  currentPage.value = page
  if (hasSearchQuery.value) {
    doSearch()
  }
}

function clearSearch() {
  searchQuery.value = ''
  searchResults.value = []
  searchTotal.value = 0
  currentPage.value = 1
}

function searchByTag(tag: string) {
  searchQuery.value = tag
  handleSearch()
}

// === 详情导航 ===

function goToDetail(id: string) {
  router.push({ name: 'knowledge-detail', params: { id } })
}

// === 删除 ===

async function handleDelete(id: string) {
  try {
    await deleteKnowledge(id)
    window.$message?.success('已删除')
    // 刷新列表
    await loadEntries()
    // 如果在搜索模式，也刷新搜索结果
    if (hasSearchQuery.value) {
      await doSearch()
    }
  } catch {
    // 错误已由拦截器处理
  }
}

// === 上传 ===

async function handleUpload(options: UploadCustomRequestOptions) {
  const { file } = options
  uploading.value = true
  try {
    const result = await uploadKnowledge(file.file as File)
    window.$message?.success(`已上传: ${result.title}`)
    await loadEntries()
  } catch {
    options.onError()
  } finally {
    uploading.value = false
  }
}

// === 格式化 ===

function sourceLabel(source: string | null): string {
  const map: Record<string, string> = {
    upload: '上传',
    submission: '提交',
    manual: '手动'
  }
  return source ? (map[source] || source) : ''
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// === 生命周期 ===

watch(searchQuery, (val) => {
  if (!val.trim() && searchResults.value.length > 0) {
    clearSearch()
  }
})

onMounted(async () => {
  await loadEntries()
  // 处理来自详情页的搜索跳转（如点击 wikilink）
  const q = route.query.q as string | undefined
  if (q) {
    searchQuery.value = q
    handleSearch()
  }
})
</script>

<template>
  <div class="kb-page">
    <n-grid :cols="1" :x-gap="16" responsive="screen" item-responsive>
      <!-- 主内容区 -->
      <n-gi span="1" class="kb-main">
        <!-- 搜索栏 + 上传 -->
        <n-card :bordered="false" class="kb-toolbar" size="small">
          <n-space align="center" justify="space-between">
            <n-input-group style="flex: 1">
              <n-input
                v-model:value="searchQuery"
                placeholder="搜索知识库（支持中文全文检索）..."
                clearable
                @keyup.enter="handleSearch"
                @clear="clearSearch"
              >
                <template #prefix>
                  <n-icon :component="SearchOutline" />
                </template>
              </n-input>
              <n-button type="primary" :loading="isSearching" @click="handleSearch">
                搜索
              </n-button>
            </n-input-group>
            <n-upload
              :custom-request="handleUpload"
              accept=".md,.txt,.markdown"
              :show-file-list="false"
              :disabled="uploading"
            >
              <n-button type="info" :loading="uploading">
                <template #icon><n-icon :component="CloudUploadOutline" /></template>
                上传笔记
              </n-button>
            </n-upload>
          </n-space>
        </n-card>

        <!-- 搜索结果 -->
        <template v-if="hasSearchQuery">
          <n-text depth="3" style="display: block; margin: 12px 0 8px">
            搜索「{{ searchQuery }}」找到 {{ searchTotal }} 条结果
            <n-button text type="primary" size="small" @click="clearSearch">清除搜索</n-button>
          </n-text>

          <n-spin v-if="isSearching" size="large" style="display: flex; justify-content: center; padding: 60px 0" />

          <n-empty v-else-if="searchResults.length === 0" description="未找到匹配的知识条目" style="padding: 60px 0" />

          <n-space v-else vertical :size="12">
            <n-card
              v-for="item in searchResults"
              :key="item.id"
              :bordered="false"
              class="kb-entry-card"
              hoverable
              size="small"
              @click="goToDetail(item.id)"
            >
              <template #header>
                <n-space align="center" :size="8">
                  <n-icon :component="DocumentTextOutline" color="#18a058" />
                  <span class="entry-title">{{ item.title }}</span>
                </n-space>
              </template>
              <template #header-extra>
                <n-space align="center" :size="8">
                  <n-tag size="tiny" round>{{ sourceLabel(item.sourceType) }}</n-tag>
                  <n-text depth="3" style="font-size: 12px">{{ formatDate(item.createdAt) }}</n-text>
                </n-space>
              </template>

              <!-- 高亮摘要 -->
              <div class="entry-summary" v-html="item.highlightedSummary"></div>

              <!-- 标签 -->
              <n-space v-if="item.tags && item.tags.length > 0" style="margin-top: 8px" :size="4">
                <n-tag
                  v-for="tag in item.tags"
                  :key="tag"
                  size="tiny"
                  :bordered="false"
                  type="info"
                  round
                  @click.stop="searchByTag(tag)"
                >
                  {{ tag }}
                </n-tag>
              </n-space>
            </n-card>
          </n-space>

          <!-- 分页 -->
          <div v-if="searchTotal > pageSize" style="margin-top: 16px; display: flex; justify-content: center">
            <n-pagination
              v-model:page="currentPage"
              :page-count="Math.ceil(searchTotal / pageSize)"
              :page-size="pageSize"
              @update:page="handlePageChange"
            />
          </div>
        </template>

        <!-- 全部条目列表 -->
        <template v-else>
          <n-spin v-if="listLoading" size="large" style="display: flex; justify-content: center; padding: 60px 0" />

          <n-empty
            v-else-if="entries.length === 0"
            description="知识库还是空的，上传你的第一篇笔记吧！"
            style="padding: 60px 0"
          >
            <template #extra>
              <n-upload
                :custom-request="handleUpload"
                accept=".md,.txt,.markdown"
                :show-file-list="false"
                :disabled="uploading"
              >
                <n-button type="primary" :loading="uploading">
                  <template #icon><n-icon :component="CloudUploadOutline" /></template>
                  上传笔记
                </n-button>
              </n-upload>
            </template>
          </n-empty>

          <n-space v-else vertical :size="12">
            <n-card
              v-for="entry in entries"
              :key="entry.id"
              :bordered="false"
              class="kb-entry-card"
              hoverable
              size="small"
              @click="goToDetail(entry.id)"
            >
              <template #header>
                <n-space align="center" :size="8">
                  <n-icon :component="DocumentTextOutline" color="#18a058" />
                  <span class="entry-title">{{ entry.title }}</span>
                </n-space>
              </template>
              <template #header-extra>
                <n-space align="center" :size="8">
                  <n-tag size="tiny" round>{{ sourceLabel(entry.sourceType) }}</n-tag>
                  <n-text depth="3" style="font-size: 12px">
                    <n-icon :component="TimeOutline" size="12" />
                    {{ formatDate(entry.createdAt) }}
                  </n-text>
                  <n-popconfirm @positive-click="handleDelete(entry.id)">
                    <template #trigger>
                      <n-button text size="tiny" type="error" @click.stop>
                        <n-icon :component="TrashOutline" size="14" />
                      </n-button>
                    </template>
                    确定删除「{{ entry.title }}」？
                  </n-popconfirm>
                </n-space>
              </template>

              <!-- 摘要 -->
              <n-text depth="2" style="font-size: 13px; line-height: 1.6">
                {{ entry.summary || entry.tags?.join(' · ') || '暂无摘要' }}
              </n-text>

              <!-- 标签 -->
              <n-space v-if="entry.tags && entry.tags.length > 0" style="margin-top: 8px" :size="4">
                <n-tag
                  v-for="tag in entry.tags"
                  :key="tag"
                  size="tiny"
                  :bordered="false"
                  type="info"
                  round
                  @click.stop="searchByTag(tag)"
                >
                  {{ tag }}
                </n-tag>
              </n-space>
            </n-card>
          </n-space>
        </template>
      </n-gi>

      <!-- 侧边栏 -->
      <n-gi span="1" class="kb-sidebar">
        <!-- 标签云 -->
        <n-card :bordered="false" size="small" title="标签云">
          <template #header-extra>
            <n-icon :component="PricetagsOutline" />
          </template>
          <n-spin v-if="listLoading" size="small" />
          <n-empty v-else-if="tagCloud.length === 0" description="暂无标签" size="small" />
          <n-space v-else :size="6" wrap>
            <n-tag
              v-for="[tag, count] in tagCloud"
              :key="tag"
              :size="count > 2 ? 'medium' : 'small'"
              :type="count > 2 ? 'success' : 'default'"
              round
              checkable
              @click="searchByTag(tag)"
            >
              {{ tag }}
              <span class="tag-count">{{ count }}</span>
            </n-tag>
          </n-space>
        </n-card>

        <n-divider style="margin: 12px 0" />

        <!-- 最近编辑 -->
        <n-card :bordered="false" size="small" title="最近编辑">
          <template #header-extra>
            <n-icon :component="TimeOutline" />
          </template>
          <n-spin v-if="listLoading" size="small" />
          <n-empty v-else-if="recentEntries.length === 0" description="暂无记录" size="small" />
          <n-space v-else vertical :size="8">
            <div
              v-for="entry in recentEntries"
              :key="entry.id"
              class="recent-item"
              @click="goToDetail(entry.id)"
            >
              <n-text class="recent-title" ellipsis>{{ entry.title }}</n-text>
              <n-text depth="3" style="font-size: 11px">
                <n-time :time="new Date(entry.updatedAt)" :to="new Date()" type="relative" />
              </n-text>
            </div>
          </n-space>
        </n-card>
      </n-gi>
    </n-grid>
  </div>
</template>

<style scoped lang="scss">
.kb-page {
  max-width: 1200px;
  margin: 0 auto;
}

/* 响应式：大屏幕时显示侧边栏 */
@media (min-width: 1024px) {
  .kb-page :deep(.n-grid) {
    display: grid;
    grid-template-columns: 1fr 280px;
    gap: 16px;
  }
  .kb-main {
    grid-column: 1;
  }
  .kb-sidebar {
    grid-column: 2;
  }
}

.kb-toolbar {
  margin-bottom: 16px;
}

.kb-entry-card {
  cursor: pointer;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  }
}

.entry-title {
  font-weight: 600;
  font-size: 15px;
}

.entry-summary {
  font-size: 13px;
  line-height: 1.6;
  color: #555;

  :deep(mark) {
    background: rgba(255, 213, 79, 0.4);
    color: inherit;
    padding: 0 2px;
    border-radius: 2px;
    font-weight: 600;
  }
}

.tag-count {
  margin-left: 4px;
  font-size: 10px;
  opacity: 0.6;
}

.recent-item {
  cursor: pointer;
  padding: 4px 0;

  &:hover .recent-title {
    color: #18a058;
  }
}

.recent-title {
  display: block;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s;
}
</style>
