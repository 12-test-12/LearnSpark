<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  NIcon, NSpin, NEmpty, NTag, NButton, NProgress, NCard
} from 'naive-ui'
import {
  CalendarOutline, FlameOutline, RibbonOutline,
  BookOutline, SparklesOutline, ArrowForwardOutline, PlayCircleOutline,
  AddOutline, FolderOpenOutline
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
  const done = dashboard.value.todayTasks.filter(t => t.status === 'passed' || t.status === 'PASSED').length
  return Math.round((done / total) * 100)
})

/** 今日完成数（用于 "0/3" 展示） */
const todayDoneCount = computed(() => {
  if (!dashboard.value) return 0
  return dashboard.value.todayTasks.filter(t => t.status === 'passed' || t.status === 'PASSED').length
})

/** 是否需要空数据引导（数据库是空的） */
const isFirstTime = computed(() => {
  if (!dashboard.value) return false
  return dashboard.value.totalPoints === 0
    && dashboard.value.knowledgeCount === 0
    && dashboard.value.todayTasks.length === 0
    && dashboard.value.maxStreakDays === 0
})

/** 状态标签映射（兼容 passed / PASSED 两种大小写） */
function statusInfo(status: string): { label: string; type: 'default' | 'success' | 'warning' | 'info' | 'error' } {
  const normalized = (status || '').toUpperCase()
  const map: Record<string, { label: string; type: 'default' | 'success' | 'warning' | 'info' | 'error' }> = {
    PASSED:    { label: '已完成', type: 'success' },
    SUBMITTED: { label: '审核中', type: 'warning' },
    PENDING:   { label: '未开始', type: 'default' },
    REJECTED:  { label: '未通过', type: 'error' },
  }
  return map[normalized] ?? { label: status || '未知', type: 'default' }
}

function goToSubmit(taskId: string) {
  router.push({ name: 'task-submit', params: { taskId } })
}

function goToProjects() {
  router.push({ name: 'projects' })
}

function goToCreateProject() {
  router.push({ name: 'projects' })
}

onMounted(loadData)
</script>

<template>
  <div class="dashboard-page">
    <!-- 加载中 -->
    <div v-if="loading" class="loading-wrapper">
      <n-spin size="large" />
    </div>

    <template v-else-if="dashboard">
      <!-- 1. 每日一句 · 通栏 Banner -->
      <div class="quote-banner">
        <div class="quote-mark">"</div>
        <div class="quote-text">{{ dashboard.dailyQuote }}</div>
        <div class="quote-sub">— LearnSpark · 每日灵感</div>
      </div>

      <!-- 2. 首次使用引导（空数据库时显示） -->
      <div v-if="isFirstTime" class="onboarding-card">
        <div class="onboarding-icon">
          <n-icon :component="SparklesOutline" :size="32" color="#18a058" />
        </div>
        <div class="onboarding-content">
          <div class="onboarding-title">开启你的学习之旅</div>
          <div class="onboarding-desc">先创建一个学习项目，可以手动添加任务或让 AI 帮你生成学习计划</div>
        </div>
        <n-button type="primary" size="medium" @click="goToCreateProject">
          <template #icon>
            <n-icon :component="AddOutline" />
          </template>
          创建项目
        </n-button>
      </div>

      <!-- 3. 顶部三栏统计卡片 -->
      <div class="stat-cards">
        <div class="stat-card stat-card-green">
          <div class="stat-icon">
            <n-icon :component="CalendarOutline" :size="22" />
          </div>
          <div class="stat-body">
            <div class="stat-label">今日待完成</div>
            <div class="stat-value">
              <span class="num">{{ dashboard.todayPendingCount }}</span>
              <span class="unit">项</span>
            </div>
          </div>
        </div>

        <div class="stat-card stat-card-orange">
          <div class="stat-icon">
            <n-icon :component="FlameOutline" :size="22" />
          </div>
          <div class="stat-body">
            <div class="stat-label">连续打卡</div>
            <div class="stat-value">
              <span class="num">{{ dashboard.maxStreakDays }}</span>
              <span class="unit">天</span>
            </div>
            <div v-if="dashboard.maxStreakDays === 0" class="stat-hint">开启第一天</div>
          </div>
        </div>

        <div class="stat-card stat-card-amber">
          <div class="stat-icon">
            <n-icon :component="RibbonOutline" :size="22" />
          </div>
          <div class="stat-body">
            <div class="stat-label">总积分</div>
            <div class="stat-value">
              <span class="num">{{ dashboard.totalPoints }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 4. 主体：今日任务（置顶，移动端用户最关心的） -->
      <div class="section-card section-card-priority">
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
            :height="4"
            color="#18a058"
            rail-color="rgba(255,255,255,0.06)"
            class="section-progress"
          />
        </div>

        <n-empty
          v-if="dashboard.todayTasks.length === 0"
          :show-icon="false"
          size="small"
          class="task-empty"
        >
          <template #extra>
            <div class="empty-content">
              <n-icon :component="FolderOpenOutline" :size="40" color="#4b5563" />
              <div class="empty-text">今日暂无任务</div>
              <div class="empty-sub">去项目里看看，规划一下明天的学习</div>
              <n-button type="primary" size="small" @click="goToProjects" style="margin-top: 12px;">
                查看项目
              </n-button>
            </div>
          </template>
        </n-empty>

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
              v-if="task.status === 'pending' || task.status === 'PENDING' || task.status === 'failed' || task.status === 'REJECTED'"
              type="primary"
              size="small"
              @click.stop="goToSubmit(task.id)"
              class="task-action-btn"
            >
              <template #icon>
                <n-icon :component="PlayCircleOutline" />
              </template>
              <span class="btn-text">开始</span>
            </n-button>
            <n-icon v-else :component="ArrowForwardOutline" :size="18" class="task-arrow" />
          </div>
        </div>
      </div>

      <!-- 5. 主体：学习日历热力图 -->
      <div class="section-card">
        <div class="section-header">
          <div class="section-title">
            <n-icon :component="CalendarOutline" :size="18" color="#18a058" />
            <span>学习日历</span>
            <span class="section-sub">近 90 天</span>
          </div>
        </div>
        <StreakCalendar :activities="activities" />
      </div>

      <!-- 6. 知识库快捷入口（移到底部，单行紧凑布局） -->
      <div class="kb-card" @click="router.push({ name: 'knowledge' })">
        <div class="kb-icon">
          <n-icon :component="BookOutline" :size="20" color="#18a058" />
        </div>
        <div class="kb-text">
          <div class="kb-num">{{ dashboard.knowledgeCount }} <span class="kb-unit">条</span></div>
          <div class="kb-label">已沉淀知识</div>
        </div>
        <n-icon :component="ArrowForwardOutline" :size="18" class="kb-arrow" />
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
/* ========== 基础 ========== */
.dashboard-page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 0 80px 0;  // 底部留出移动端导航栏的高度
}

.loading-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

/* ========== 1. 每日一句 Banner ========== */
.quote-banner {
  position: relative;
  margin-bottom: 16px;
  padding: 20px 24px 18px;
  background: linear-gradient(135deg, #1a1a1a 0%, #161616 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  overflow: hidden;

  .quote-mark {
    position: absolute;
    top: -10px;
    left: 16px;
    font-family: Georgia, 'Times New Roman', serif;
    font-size: 100px;
    line-height: 1;
    color: rgba(24, 160, 88, 0.12);
    pointer-events: none;
  }

  .quote-text {
    position: relative;
    font-family: Georgia, 'Times New Roman', 'Source Han Serif SC', serif;
    font-size: 16px;
    line-height: 1.6;
    color: #e5e7eb;
    font-style: italic;
    padding-left: 8px;
  }

  .quote-sub {
    margin-top: 8px;
    font-size: 12px;
    color: #6b7280;
    letter-spacing: 1px;
  }
}

/* ========== 2. 首次使用引导 ========== */
.onboarding-card {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  padding: 16px 20px;
  background: linear-gradient(135deg, rgba(24, 160, 88, 0.08), rgba(24, 160, 88, 0.02));
  border: 1px solid rgba(24, 160, 88, 0.25);
  border-radius: 12px;
}

.onboarding-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(24, 160, 88, 0.14);
  border-radius: 10px;
  flex-shrink: 0;
}

.onboarding-content {
  flex: 1;
  min-width: 0;
}

.onboarding-title {
  font-size: 15px;
  font-weight: 600;
  color: #f3f4f6;
  margin-bottom: 4px;
}

.onboarding-desc {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.5;
}

/* ========== 3. 顶部统计卡片 ========== */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  background: #1e1e1e;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  transition: transform 0.2s, border-color 0.2s;

  &:hover {
    transform: translateY(-2px);
    border-color: rgba(255, 255, 255, 0.15);
  }
}

.stat-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  flex-shrink: 0;
}

.stat-card-green .stat-icon { background: rgba(24, 160, 88, 0.14); color: #36d57f; }
.stat-card-orange .stat-icon { background: rgba(240, 160, 32, 0.14); color: #f0a020; }
.stat-card-amber .stat-icon { background: rgba(245, 158, 11, 0.14); color: #f59e0b; }

.stat-body { flex: 1; min-width: 0; }

.stat-label {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 4px;
  letter-spacing: 0.3px;
}

.stat-value {
  font-family: 'JetBrains Mono', 'Roboto Mono', 'SF Mono', Consolas, monospace;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  color: #f3f4f6;
  display: flex;
  align-items: baseline;
  gap: 4px;

  .unit {
    font-size: 13px;
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

/* ========== 4. 通用 section 卡片 ========== */
.section-card {
  background: #1e1e1e;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 18px 20px;
  margin-bottom: 14px;
}

.section-card-priority {
  border-color: rgba(24, 160, 88, 0.2);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
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

.section-progress {
  flex: 1;
  max-width: 140px;
  min-width: 80px;
}

/* ========== 今日任务 ========== */
.task-empty {
  padding: 16px 0 8px;
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
}

.empty-text {
  font-size: 14px;
  color: #d1d5db;
  margin-top: 12px;
}

.empty-sub {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, transform 0.15s;

  &:active {
    transform: scale(0.98);
  }

  &:hover {
    background: rgba(24, 160, 88, 0.05);
    border-color: rgba(24, 160, 88, 0.3);
  }
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.task-title {
  font-weight: 600;
  font-size: 14px;
  color: #f3f4f6;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  flex-shrink: 1;
  min-width: 0;
}

.task-desc {
  font-size: 12px;
  line-height: 1.5;
  color: #6b7280;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-word;
}

.task-action-btn {
  flex-shrink: 0;
}

.task-arrow {
  color: #6b7280;
  transition: color 0.2s, transform 0.2s;
  flex-shrink: 0;
}
.task-item:hover .task-arrow { color: #36d57f; transform: translateX(2px); }

/* ========== 5. 知识库快捷入口 ========== */
.kb-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  background: #1e1e1e;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;

  &:hover {
    background: rgba(24, 160, 88, 0.05);
    border-color: rgba(24, 160, 88, 0.3);
  }
}

.kb-icon {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(24, 160, 88, 0.14);
  border-radius: 8px;
  flex-shrink: 0;
}

.kb-text {
  flex: 1;
  min-width: 0;
}

.kb-num {
  font-family: 'JetBrains Mono', 'Roboto Mono', 'SF Mono', Consolas, monospace;
  font-size: 18px;
  font-weight: 700;
  color: #f3f4f6;
  line-height: 1.2;

  .kb-unit {
    font-size: 12px;
    font-weight: 400;
    color: #9ca3af;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  }
}

.kb-label {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 2px;
}

.kb-arrow {
  color: #6b7280;
  flex-shrink: 0;
}

/* ========== 移动端适配（核心改动）========== */
@media (max-width: 720px) {
  .dashboard-page {
    padding: 0 0 80px 0;
  }

  .quote-banner {
    padding: 16px 18px 14px;
    margin-bottom: 12px;

    .quote-text { font-size: 14px; }
  }

  .onboarding-card {
    padding: 14px 16px;
    gap: 12px;
    flex-wrap: wrap;

    .onboarding-icon {
      width: 38px;
      height: 38px;
    }
    .onboarding-title { font-size: 14px; }
    .onboarding-desc { font-size: 11px; }
  }

  // 关键：移动端 3 卡片仍然横排，但更紧凑
  .stat-cards {
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    margin-bottom: 12px;
  }

  .stat-card {
    padding: 12px 10px;
    gap: 8px;
    flex-direction: column;
    align-items: flex-start;
  }

  .stat-icon {
    width: 32px;
    height: 32px;
  }

  .stat-label { font-size: 11px; }
  .stat-value {
    font-size: 20px;
    .unit { font-size: 12px; }
  }

  .section-card {
    padding: 14px 16px;
    margin-bottom: 12px;
  }

  .section-header { margin-bottom: 12px; }

  .section-title { font-size: 14px; }

  // 进度条移到下一行
  .section-progress {
    flex-basis: 100%;
    max-width: 100%;
  }

  .task-item {
    padding: 10px 12px;
    gap: 8px;
  }

  .task-title { font-size: 13px; }
  .task-desc { font-size: 11px; }

  // 关键：移动端任务按钮的"开始"文字隐藏，只显示图标
  .task-action-btn :deep(.btn-text) {
    display: none;
  }

  .kb-card {
    padding: 12px 14px;
  }

  .kb-num { font-size: 16px; }
}

/* 超小屏（iPhone SE 等 320px 设备）*/
@media (max-width: 380px) {
  .stat-cards { gap: 6px; }
  .stat-card { padding: 10px 8px; }
  .stat-icon { width: 28px; height: 28px; }
  .stat-value { font-size: 18px; }
  .stat-label { font-size: 10px; }
}
</style>
