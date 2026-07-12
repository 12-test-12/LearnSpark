<script setup lang="ts">
import { computed } from 'vue'
import { NCard, NTag, NText, NSpace, NButton, NIcon, NPopconfirm } from 'naive-ui'
import {
  CheckmarkCircleOutline, CloseCircleOutline, CreateOutline, TrashOutline,
  CalendarOutline, FlagOutline, RibbonOutline, HourglassOutline
} from '@vicons/ionicons5'
import type { Task, TaskStatus } from '@/api/project'

const props = defineProps<{
  task: Task
  /** 是否显示操作按钮（编辑/删除），默认 true */
  showActions?: boolean
}>()

const emit = defineEmits<{
  submit: [task: Task]
  edit: [task: Task]
  delete: [task: Task]
}>()

const statusConfig: Record<TaskStatus, { label: string; type: 'default' | 'warning' | 'success' | 'error'; color: string }> = {
  pending: { label: '待提交', type: 'default', color: '#909399' },
  submitted: { label: '已提交', type: 'warning', color: '#e6a23c' },
  passed: { label: '已通过', type: 'success', color: '#18a058' },
  failed: { label: '未通过', type: 'error', color: '#d03050' }
}

const statusInfo = computed(() => statusConfig[props.task.status] || statusConfig.pending)
const showActions = computed(() => props.showActions !== false)

const canSubmit = computed(() => props.task.status === 'pending' || props.task.status === 'failed')

function formatDate(dateStr: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function handleSubmit() {
  emit('submit', props.task)
}

function handleEdit() {
  emit('edit', props.task)
}

function handleDelete() {
  emit('delete', props.task)
}
</script>

<template>
  <n-card
    class="task-card"
    size="small"
    :bordered="true"
    :style="{ borderLeft: `3px solid ${statusInfo.color}` }"
  >
    <div class="task-card-header">
      <n-space align="center" size="small" wrap>
        <n-icon v-if="task.status === 'passed'" :component="CheckmarkCircleOutline" :color="statusInfo.color" />
        <n-icon v-else-if="task.status === 'failed'" :component="CloseCircleOutline" :color="statusInfo.color" />
        <n-icon v-else :component="HourglassOutline" :color="statusInfo.color" />
        <n-text strong>{{ task.title || '未命名任务' }}</n-text>
        <n-tag v-if="task.dayNumber" size="tiny" round type="info">Day {{ task.dayNumber }}</n-tag>
      </n-space>
      <n-tag :type="statusInfo.type" size="small" round>{{ statusInfo.label }}</n-tag>
    </div>

    <div class="task-card-body">
      <n-text depth="2" class="task-desc">{{ task.description }}</n-text>
    </div>

    <div v-if="task.verificationCriteria" class="task-card-criteria">
      <n-space align="center" size="small">
        <n-icon :component="FlagOutline" size="14" color="#909399" />
        <n-text depth="3" style="font-size: 12px">{{ task.verificationCriteria }}</n-text>
      </n-space>
    </div>

    <div class="task-card-footer">
      <n-space align="center" size="small">
        <n-text v-if="task.dueDate" depth="3" style="font-size: 12px">
          <n-icon :component="CalendarOutline" size="12" style="vertical-align: -2px" />
          {{ formatDate(task.dueDate) }}
        </n-text>
        <n-text v-if="task.completedAt" depth="3" style="font-size: 12px">
          <n-icon :component="RibbonOutline" size="12" style="vertical-align: -2px" />
          完成 {{ formatDate(task.completedAt) }}
        </n-text>
      </n-space>

      <n-space v-if="showActions" size="small">
        <n-button v-if="canSubmit" size="small" type="primary" @click="handleSubmit">
          提交
        </n-button>
        <n-button v-else-if="task.status === 'passed'" size="small" disabled>
          已通过
        </n-button>
        <n-button size="small" quaternary @click="handleEdit">
          <template #icon><n-icon :component="CreateOutline" /></template>
        </n-button>
        <n-popconfirm @positive-click="handleDelete">
          <template #trigger>
            <n-button size="small" quaternary type="error">
              <template #icon><n-icon :component="TrashOutline" /></template>
            </n-button>
          </template>
          确认删除该任务？
        </n-popconfirm>
      </n-space>
    </div>
  </n-card>
</template>

<style scoped lang="scss">
.task-card {
  border-radius: 8px;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  }
}

.task-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.task-card-body {
  margin-bottom: 8px;
}

.task-desc {
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.task-card-criteria {
  margin-bottom: 8px;
  padding: 6px 8px;
  background: rgba(128, 128, 128, 0.05);
  border-radius: 4px;
}

.task-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
