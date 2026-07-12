<script setup lang="ts">
import { computed, ref } from 'vue'
import type { DailyActivity } from '@/api/stats'

interface Props {
  activities: DailyActivity[]
}

const props = defineProps<Props>()

/** 热力图展示周数（13 周 ≈ 90 天） */
const WEEKS = 13

/** 活动日期 → 数量映射（SSOT，避免重复查找） */
const activityMap = computed(() => {
  const map = new Map<string, number>()
  props.activities.forEach((a) => map.set(a.date, a.count))
  return map
})

/** 日期网格：按周分列，每列 7 天（周日→周六） */
const weeks = computed(() => buildWeekGrid())

/** 当前 hover 的格子信息（用于浮层 Tooltip） */
const hoverInfo = ref<{ x: number; y: number; date: string; count: number } | null>(null)

/** 构建日期网格：从 13 周前的周日开始 */
function buildWeekGrid(): Date[][] {
  const today = new Date()
  const todayWeekday = today.getDay()
  const startDate = new Date(today)
  startDate.setDate(today.getDate() - todayWeekday - (WEEKS - 1) * 7)
  const grid: Date[][] = []
  for (let w = 0; w < WEEKS; w++) {
    const week: Date[] = []
    for (let d = 0; d < 7; d++) {
      const date = new Date(startDate)
      date.setDate(startDate.getDate() + w * 7 + d)
      week.push(date)
    }
    grid.push(week)
  }
  return grid
}

/** 获取某天的活动数量 */
function getCount(date: Date): number {
  return activityMap.value.get(formatDateKey(date)) ?? 0
}

/** 根据数量返回颜色等级（0=无活动, 4=高频） */
function getLevel(count: number): number {
  if (count === 0) return 0
  if (count === 1) return 1
  if (count <= 3) return 2
  if (count <= 5) return 3
  return 4
}

/** 格式化日期为 YYYY-MM-DD（本地时区，避免 UTC 偏移） */
function formatDateKey(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 格式化日期为可读文字（用于 tooltip） */
function formatDateLabel(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/** 判断日期是否在今天之后（不渲染未来的格子） */
function isFuture(date: Date): boolean {
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return date > today
}

/** 判断是否为今天 */
function isToday(date: Date): boolean {
  const today = new Date()
  return (
    date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth() &&
    date.getDate() === today.getDate()
  )
}

/** 显示浮层 */
function showTooltip(e: MouseEvent, day: Date) {
  if (isFuture(day)) {
    hoverInfo.value = null
    return
  }
  const cell = e.currentTarget as HTMLElement
  const rect = cell.getBoundingClientRect()
  hoverInfo.value = {
    x: rect.left + rect.width / 2,
    y: rect.top,
    date: formatDateLabel(day),
    count: getCount(day),
  }
}

/** 隐藏浮层 */
function hideTooltip() {
  hoverInfo.value = null
}
</script>

<template>
  <div class="streak-calendar">
    <div class="calendar-wrapper">
      <!-- 星期标签（仅显示 一/三/五） -->
      <div class="weekday-labels">
        <span></span>
        <span>一</span>
        <span></span>
        <span>三</span>
        <span></span>
        <span>五</span>
        <span></span>
      </div>
      <!-- 日期网格 -->
      <div class="calendar-grid">
        <div v-for="(week, wi) in weeks" :key="wi" class="calendar-week">
          <div
            v-for="(day, di) in week"
            :key="di"
            class="calendar-cell"
            :class="[
              `level-${getLevel(getCount(day))}`,
              { 'cell-future': isFuture(day), 'cell-today': isToday(day) }
            ]"
            @mouseenter="showTooltip($event, day)"
            @mouseleave="hideTooltip"
          ></div>
        </div>
      </div>
    </div>
    <!-- 图例 -->
    <div class="calendar-legend">
      <span class="legend-text">少</span>
      <div class="calendar-cell level-0"></div>
      <div class="calendar-cell level-1"></div>
      <div class="calendar-cell level-2"></div>
      <div class="calendar-cell level-3"></div>
      <div class="calendar-cell level-4"></div>
      <span class="legend-text">多</span>
    </div>

    <!-- 自定义浮层 Tooltip -->
    <transition name="tooltip-fade">
      <div
        v-if="hoverInfo"
        class="cell-tooltip"
        :style="{
          left: hoverInfo.x + 'px',
          top: hoverInfo.y + 'px',
        }"
      >
        <div class="tooltip-date">{{ hoverInfo.date }}</div>
        <div class="tooltip-content">
          <span class="tooltip-count">{{ hoverInfo.count }}</span>
          <span class="tooltip-label">次提交</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped lang="scss">
.calendar-wrapper {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding: 4px 0 8px;
}

.weekday-labels {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
  color: #6b7280;
  flex-shrink: 0;

  span {
    height: 18px;
    line-height: 18px;
  }
}

.calendar-grid {
  display: flex;
  gap: 4px;
}

.calendar-week {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.calendar-cell {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  transition: transform 0.15s, box-shadow 0.15s;
  cursor: pointer;

  &:hover {
    transform: scale(1.4);
    box-shadow: 0 0 0 2px rgba(24, 160, 88, 0.3);
  }

  /* 强对比的 5 级色阶：无活动=深灰，1=深绿，2=中绿，3=亮绿，4=荧光绿 */
  &.level-0 { background: rgba(255, 255, 255, 0.06); }
  &.level-1 { background: rgba(24, 160, 88, 0.35); }
  &.level-2 { background: rgba(24, 160, 88, 0.6); }
  &.level-3 { background: rgba(24, 160, 88, 0.85); }
  &.level-4 {
    background: #36d57f;
    box-shadow: 0 0 6px rgba(54, 213, 127, 0.5);
  }

  &.cell-future {
    opacity: 0;
    pointer-events: none;
  }

  &.cell-today {
    outline: 2px solid #18a058;
    outline-offset: 1px;
  }
}

.calendar-legend {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 12px;
  justify-content: flex-end;

  .legend-text {
    font-size: 11px;
    color: #6b7280;
    margin: 0 2px;
  }

  .calendar-cell {
    width: 12px;
    height: 12px;

    &:hover {
      transform: none;
      box-shadow: none;
    }
  }
}

/* 自定义 Tooltip 浮层 */
.cell-tooltip {
  position: fixed;
  transform: translate(-50%, calc(-100% - 8px));
  background: rgba(20, 20, 20, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  padding: 6px 10px;
  pointer-events: none;
  z-index: 9999;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);

  .tooltip-date {
    font-size: 11px;
    color: #9ca3af;
    margin-bottom: 2px;
  }

  .tooltip-content {
    display: flex;
    align-items: baseline;
    gap: 4px;
  }

  .tooltip-count {
    font-size: 16px;
    font-weight: 700;
    color: #36d57f;
    font-family: 'JetBrains Mono', 'Roboto Mono', 'SF Mono', Consolas, monospace;
  }

  .tooltip-label {
    font-size: 11px;
    color: #d1d5db;
  }

  /* 小三角 */
  &::after {
    content: '';
    position: absolute;
    bottom: -5px;
    left: 50%;
    transform: translateX(-50%) rotate(45deg);
    width: 8px;
    height: 8px;
    background: rgba(20, 20, 20, 0.96);
    border-right: 1px solid rgba(255, 255, 255, 0.1);
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  }
}

.tooltip-fade-enter-active,
.tooltip-fade-leave-active {
  transition: opacity 0.15s ease;
}
.tooltip-fade-enter-from,
.tooltip-fade-leave-to {
  opacity: 0;
}
</style>
