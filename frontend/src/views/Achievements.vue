<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  NCard, NSpace, NText, NTag, NIcon, NSpin, NProgress, NEmpty, NTime
} from 'naive-ui'
import {
  TrophyOutline, FlameOutline, RibbonOutline, BookOutline,
  FlagOutline, LockClosedOutline
} from '@vicons/ionicons5'
import { getBadges, type BadgeItem } from '@/api/gamification'

const loading = ref(true)
const badges = ref<BadgeItem[]>([])

const earnedCount = computed(() => badges.value.filter((b) => b.awarded).length)
const totalCount = computed(() => badges.value.length)
const progressPercentage = computed(() =>
  totalCount.value > 0 ? Math.round((earnedCount.value / totalCount.value) * 100) : 0
)

/** 按分类分组徽章 */
const badgesByCategory = computed(() => {
  const groups = new Map<string, BadgeItem[]>()
  badges.value.forEach((b) => {
    const cat = b.category || 'other'
    if (!groups.has(cat)) groups.set(cat, [])
    groups.get(cat)!.push(b)
  })
  return [...groups.entries()]
})

const categoryLabels: Record<string, string> = {
  milestone: '里程碑',
  streak: '连续打卡',
  score: '积分成就',
  knowledge: '知识积累',
  count: '任务达人',
  other: '其他'
}

/** 分类对应的图标 */
const categoryIcons: Record<string, typeof TrophyOutline> = {
  milestone: FlagOutline,
  streak: FlameOutline,
  score: RibbonOutline,
  knowledge: BookOutline,
  count: TrophyOutline,
  other: RibbonOutline
}

/** 徽章规则的可读描述 */
function ruleHint(badge: BadgeItem): string {
  const { ruleType, ruleValue } = badge
  if (!ruleType || !ruleValue) return ''
  const map: Record<string, string> = {
    count: `完成 ${ruleValue} 个任务`,
    streak: `连续打卡 ${ruleValue} 天`,
    score: `累计积分 ${ruleValue}`,
    kb: `创建 ${ruleValue} 条知识`,
    perfect: `连续 ${ruleValue} 天满分通过`
  }
  return map[ruleType] || ''
}

async function loadBadges() {
  loading.value = true
  try {
    badges.value = await getBadges()
  } finally {
    loading.value = false
  }
}

onMounted(loadBadges)
</script>

<template>
  <div class="achievements-page">
    <!-- 头部：总览 -->
    <n-card :bordered="false" class="summary-card" size="medium">
      <div class="summary-content">
        <div class="summary-left">
          <n-icon :component="TrophyOutline" :size="36" color="#18a058" />
          <div>
            <n-text strong style="font-size: 18px">成就墙</n-text>
            <n-text depth="3" style="font-size: 13px; display: block">
              已解锁 {{ earnedCount }} / {{ totalCount }} 枚徽章
            </n-text>
          </div>
        </div>
        <div class="summary-right">
          <n-progress
            type="line"
            :percentage="progressPercentage"
            :height="10"
            color="#18a058"
            :show-indicator="false"
          />
          <n-text depth="3" style="font-size: 12px">{{ progressPercentage }}%</n-text>
        </div>
      </div>
    </n-card>

    <!-- 加载中 -->
    <n-spin v-if="loading" size="large" style="display: flex; justify-content: center; padding: 80px 0" />

    <!-- 空状态 -->
    <n-empty v-else-if="badges.length === 0" description="暂无徽章数据" style="padding: 60px 0" />

    <!-- 徽章网格（按分类分组） -->
    <template v-else>
      <div
        v-for="[category, items] in badgesByCategory"
        :key="category"
        class="category-section"
      >
        <n-space align="center" :size="6" style="margin-bottom: 12px">
          <n-icon :component="categoryIcons[category] || TrophyOutline" :size="18" color="#18a058" />
          <n-text strong style="font-size: 15px">{{ categoryLabels[category] || category }}</n-text>
          <n-tag size="tiny" round :bordered="false">{{ items.length }}</n-tag>
        </n-space>

        <div class="badge-grid">
          <div
            v-for="(badge, i) in items"
            :key="badge.id"
            class="badge-card"
            :class="badge.awarded ? 'badge-earned' : 'badge-locked'"
            :style="{ '--stagger': i }"
          >
            <!-- 徽章图标 -->
            <div class="badge-icon-wrapper">
              <n-icon
                v-if="badge.awarded"
                :component="categoryIcons[category] || TrophyOutline"
                :size="36"
                color="#18a058"
              />
              <n-icon
                v-else
                :component="LockClosedOutline"
                :size="32"
                color="#bbb"
              />
            </div>

            <!-- 徽章信息 -->
            <div class="badge-name">{{ badge.name }}</div>
            <div class="badge-desc">{{ badge.description || '暂无描述' }}</div>
            <div class="badge-hint">{{ ruleHint(badge) }}</div>

            <!-- 状态标签 -->
            <div class="badge-footer">
              <template v-if="badge.awarded">
                <n-tag size="tiny" type="success" round :bordered="false">已解锁</n-tag>
                <n-text v-if="badge.awardedAt" depth="3" style="font-size: 11px">
                  <n-time :time="new Date(badge.awardedAt)" />
                </n-text>
              </template>
              <n-tag v-else size="tiny" type="default" round :bordered="false">未解锁</n-tag>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.achievements-page {
  max-width: 1000px;
  margin: 0 auto;
}

/* 头部总览 */
.summary-card {
  margin-bottom: 24px;
}

.summary-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.summary-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.summary-right {
  flex: 1;
  min-width: 200px;
  max-width: 400px;
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 分类区块 */
.category-section {
  margin-bottom: 28px;
}

/* 徽章网格：自适应列数 */
.badge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}

/* 徽章卡片 */
.badge-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 20px 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--n-border-color, #efeff5);
  background: var(--n-card-color, #fff);
  transition: transform 0.2s, box-shadow 0.2s;
  animation: badge-pop 0.5s ease-out backwards;
  animation-delay: calc(var(--stagger) * 0.08s);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  }
}

.badge-icon-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  background: rgba(128, 128, 128, 0.06);
}

.badge-name {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 4px;
}

.badge-desc {
  font-size: 12px;
  color: #999;
  line-height: 1.5;
  margin-bottom: 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.badge-hint {
  font-size: 11px;
  color: #bbb;
  margin-bottom: 8px;
}

.badge-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

/* 已解锁徽章：高亮 + 微光 */
.badge-earned {
  .badge-icon-wrapper {
    background: rgba(24, 160, 88, 0.1);
    box-shadow: 0 0 12px rgba(24, 160, 88, 0.15);
  }
}

/* 未解锁徽章：灰显 */
.badge-locked {
  opacity: 0.65;
  filter: grayscale(0.6);
}

/* 徽章入场动画 */
@keyframes badge-pop {
  0% {
    opacity: 0;
    transform: scale(0.8) translateY(10px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}
</style>
