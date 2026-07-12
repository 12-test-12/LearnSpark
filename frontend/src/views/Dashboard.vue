<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  NIcon, NSpin, NEmpty, NTag, NButton, NProgress
} from 'naive-ui'
import {
  CalendarOutline, FlameOutline, RibbonOutline,
  BookOutline, SparklesOutline, ArrowForwardOutline, PlayCircleOutline
} from '@vicons/ionicons5'
import {
  getDashboard, getActivityHeatmap,
  type DashboardData, type DailyActivity
} from '@/api/stats'
import StreakCalendar from '@/components/StreakCalendar.vue'

const router = useRouter()
const loading = ref(true)
const dashboard = ref<DashboardData | null>(null)
const activities = ref<DailyActivity[]>([])

async function loadData() {
  loading.value = true
  try {
    const [data, activity] = await Promise.all([
      getDashboard(),
      getActivityHeatmap()
    ])
    dashboard.value = data
    activities.value = activity
  } finally {
    loading.value = false
  }
}

/** 今日任务进度（0-100，用于线形进度条） */
const todayProgress = computed(() => {
  if (!dashboard.value) return 0
  const total = dashboard.value.todayTasks.length
  if (total === 0) return 100
  const done = dashboard.value.todayTasks.filter(t => t.status === 'PASSED').length
  return Math.round((done / total) * 100)
})

/** 今日完成数（用于 "0/3" 展示） */
const todayDoneCount = computed(() => {
  if (!dashboard.value) return 0
  return dashboard.value.todayTasks.filter(t => t.status === 'PASSED').length
})

/** 状态标签映射 */
function statusInfo(status: string): { label: string; type: 'default' | 'success' | 'warning' | 'info' | 'error' } {
  const map: Record<string, { label: string; type: 'default' | 'success' | 'warning' | 'info' | 'error' }> = {
    PASSED:    { label: '已完成', type: 'success' },
    SUBMITTED: { label: '审核中', type: 'warning' },
    PENDING:   { label: '未开始', type: 'default' },
    REJECTED:  { label: '未通过', type: 'error' },
  }
  return map[status] ?? { label: status, type: 'default' }
}

function goToSubmit(taskId: string) {
  router.push({ name: 'task-submit', params: { taskId } })
}

onMounted(loadData)
</script>

<template>
  <div class="dashboard-page">
    <!-- 加载中 -->
    <n-spin v-if="loading" size="large" class="loading" />

    <template v-else-if="dashboard">
      <!-- 1. 每日一句 · 通栏 Banner（先放顶部，气场） -->
      <div class="quote-banner">
        <div class="quote-mark">"</div>
        <div class="quote-text">{{ dashboard.dailyQuote }}</div>
        <div class="quote-sub">— LearnSpark · 每日灵感</div>
      </div>

      <!-- 2. 顶部三栏统计卡片 -->
      <div class="stat-cards">
        <div class="stat-card">
          <div class="stat-icon stat-icon-green">
            <n-icon :component="CalendarOutline" :size="24" />
          </div>
          <div class="stat-body">
            <div class="stat-label">今日待完成</div>
            <div class="stat-value">{{ dashboard.todayPendingCount }}</div>
          </div>
          <div class="stat-accent stat-accent-green"></div>
        </div>

        <div class="stat-card">
          <div class="stat-icon stat-icon-orange">
            <n-icon :component="FlameOutline" :size="24" />
          </div>
          <div class="stat-body">
            <div class="stat-label">连续打卡</div>
            <div class="stat-value">
              <span class="num">{{ dashboard.maxStreakDays }}</span>
              <span class="unit">天</span>
            </div>
            <div v-if="dashboard.maxStreakDays === 0" class="stat-hint">开启你的第一天！</div>
          </div>
          <div class="stat-accent stat-accent-orange"></div>
        </div>

        <div class="stat-card">
          <div class="stat-icon stat-icon-amber">
            <n-icon :component="RibbonOutline" :size="24" />
          </div>
          <div class="stat-body">
            <div class="stat-label">总积分</div>
            <div class="stat-value">{{ dashboard.totalPoints }}</div>
          </div>
          <div class="stat-accent stat-accent-amber"></div>
        </div>
      </div>

      <!-- 3. 主体：左侧热力图+任务，右侧弱化的知识库+小贴士 -->
      <div class="dashboard-grid">
        <div class="dashboard-main">
          <!-- 学习日历热力图 -->
          <div class="section-card">
            <div class="section-header">
              <div class="section-title">
                <n-icon :component="CalendarOutline" :size="18" color="#18a058" />
                <span>学习日历</span>
                <span class="section-sub">近 90 天</span>
              </div>
              <span class="section-meta">悬停查看详情</span>
            </div>
            <StreakCalendar :activities="activities" />
          </div>

          <!-- 今日任务 -->
          <div class="section-card">
            <div class="section-header">
              <div class="section-title">
                <n-icon :component="ArrowForwardOutline" :size="18" color="#18a058" />
                <span>今日任务</span>
                <n-tag
                  v-if="dashboard.todayTasks.length > 0"
                  size="tiny"
                  round
                  :bordered="false"
                  type="success"
                >
                  {{ todayDoneCount }} / {{ dashboard.todayTasks.length }}
                </n-tag>
              </div>
              <n-progress
                v-if="dashboard.todayTasks.length > 0"
                type="line"
                :percentage="todayProgress"
                :show-indicator="false"
                :height="6"
                color="#18a058"
                rail-color="rgba(255,255,255,0.06)"
                style="width: 120px"
              />
            </div>

            <n-empty
              v-if="dashboard.todayTasks.length === 0"
              description="今日暂无待完成任务，继续保持！"
              size="small"
              class="task-empty"
            />
            <div v-else class="task-list">
              <div
                v-for="task in dashboard.todayTasks"
                :key="task.id"
                class="task-item"
                @click="goToSubmit(task.id)"
              >
                <div class="task-info">
                  <div class="task-line">
                    <span class="task-title">{{ task.title || '未命名任务' }}</span>
                    <n-tag
                      size="tiny"
                      round
                      :bordered="false"
                      :type="statusInfo(task.status).type"
                    >
                      {{ statusInfo(task.status).label }}
                    </n-tag>
                  </div>
                  <div class="task-desc">{{ task.description || '暂无描述' }}</div>
                </div>
                <n-button
                  v-if="task.status === 'PENDING' || task.status === 'REJECTED'"
                  type="primary"
                  size="small"
                  @click.stop="goToSubmit(task.id)"
                >
                  <template #icon>
                    <n-icon :component="PlayCircleOutline" />
                  </template>
                  开始学习
                </n-button>
                <n-icon v-else :component="ArrowForwardOutline" :size="18" class="task-arrow" />
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：弱化存在感，改为信息流 -->
        <div class="dashboard-aside">
          <div class="aside-card">
            <div class="aside-header">
              <n-icon :component="BookOutline" :size="16" color="#18a058" />
              <span>知识库</span>
            </div>
            <div class="kb-mini">
              <div class="kb-num">{{ dashboard.knowledgeCount }}</div>
              <div class="kb-label">条已沉淀知识</div>
              <n-button text type="primary" size="small" @click="router.push({ name: 'knowledge' })">
                去查看 →
              </n-button>
            </div>
          </div>

          <div class="aside-tip">
            <n-icon :component="SparklesOutline" :size="14" />
            <span>小贴士：每日坚持提交任务可累计连续打卡天数</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
/* ========== 基础 ========== */
.dashboard-page {
  max-width: 1080px;
  margin: 0 auto;
  padding-bottom: 40px;
}

.loading {
  display: flex;
  justify-content: center;
  padding: 100px 0;
}

/* ========== 1. 每日一句 · Banner ========== */
.quote-banner {
  position: relative;
  margin-bottom: 20px;
  padding: 24px 32px 22px;
  background: linear-gradient(135deg, #1a1a1a 0%, #161616 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  overflow: hidden;

  .quote-mark {
    position: absolute;
    top: -10px;
    left: 16px;
    font-family: Georgia, 'Times New Roman', serif;
    font-size: 120px;
    line-height: 1;
    color: rgba(24, 160, 88, 0.12);
    pointer-events: none;
  }

  .quote-text {
    position: relative;
    font-family: Georgia, 'Times New Roman', 'Source Han Serif SC', serif;
    font-size: 18px;
    line-height: 1.7;
    color: #e5e7eb;
    font-style: italic;
    padding-left: 8px;
  }

  .quote-sub {
    margin-top: 10px;
    font-size: 12px;
    color: #6b7280;
    letter-spacing: 1px;
  }
}

/* ========== 2. 顶部统计卡片 ========== */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

@media (max-width: 720px) {
  .stat-cards { grid-template-columns: 1fr; }
}

.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px 24px;
  background: #1e1e1e;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  transition: transform 0.2s, border-color 0.2s;

  &:hover {
    transform: translateY(-2px);
    border-color: rgba(255, 255, 255, 0.15);
  }
}

.stat-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  flex-shrink: 0;

  &.stat-icon-green {
    background: rgba(24, 160, 88, 0.14);
    color: #36d57f;
  }
  &.stat-icon-orange {
    background: rgba(240, 160, 32, 0.14);
    color: #f0a020;
  }
  &.stat-icon-amber {
    background: rgba(245, 158, 11, 0.14);
    color: #f59e0b;
  }
}

.stat-body { flex: 1; min-width: 0; }

.stat-label {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 4px;
  letter-spacing: 0.3px;
}

.stat-value {
  font-family: 'JetBrains Mono', 'Roboto Mono', 'SF Mono', Consolas, monospace;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.1;
  color: #f3f4f6;
  display: flex;
  align-items: baseline;
  gap: 4px;

  .unit {
    font-size: 14px;
    font-weight: 400;
    color: #9ca3af;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  }
}

.stat-hint {
  font-size: 11px;
  color: #6b7280;
  margin-top: 4px;
  font-style: italic;
}

/* 卡片左侧色条 */
.stat-accent {
  position: absolute;
  left: 0;
  top: 18%;
  bottom: 18%;
  width: 3px;
  border-radius: 0 2px 2px 0;

  &.stat-accent-green  { background: #18a058; }
  &.stat-accent-orange { background: #f0a020; }
  &.stat-accent-amber  { background: #f59e0b; }
}

/* ========== 3. 主体网格 ========== */
.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
}

@media (min-width: 900px) {
  .dashboard-grid {
    grid-template-columns: 1fr 280px;
  }
}

/* ========== 通用 section 卡片 ========== */
.section-card {
  background: #1e1e1e;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 22px 24px;
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  gap: 12px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #f3f4f6;
}

.section-sub {
  font-size: 12px;
  color: #6b7280;
  font-weight: 400;
  margin-left: 4px;
}

.section-meta {
  font-size: 11px;
  color: #6b7280;
}

/* ========== 今日任务 ========== */
.task-empty {
  padding: 32px 0;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, transform 0.15s;

  &:hover {
    background: rgba(24, 160, 88, 0.05);
    border-color: rgba(24, 160, 88, 0.3);
    transform: translateX(2px);
  }
}

.task-info { flex: 1; min-width: 0; }

.task-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.task-title {
  font-weight: 600;
  font-size: 14px;
  color: #f3f4f6;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 60%;
}

.task-desc {
  font-size: 12px;
  line-height: 1.6;
  color: #6b7280;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  max-height: 2.4em;
}

.task-arrow { color: #6b7280; transition: color 0.2s, transform 0.2s; }
.task-item:hover .task-arrow { color: #36d57f; transform: translateX(2px); }

/* ========== 右侧栏（弱化） ========== */
.dashboard-aside {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.aside-card {
  background: #1e1e1e;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 18px 20px;
}

.aside-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #d1d5db;
  margin-bottom: 14px;
}

.kb-mini {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.kb-num {
  font-family: 'JetBrains Mono', 'Roboto Mono', 'SF Mono', Consolas, monospace;
  font-size: 36px;
  font-weight: 700;
  color: #36d57f;
  line-height: 1;
}

.kb-label {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.aside-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: rgba(24, 160, 88, 0.06);
  border-left: 2px solid #18a058;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #9ca3af;
}
</style>
