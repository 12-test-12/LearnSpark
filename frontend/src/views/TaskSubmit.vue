<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NCard, NSpace, NText, NTag, NInput, NDynamicTags, NButton,
  NAlert, NSpin, NResult, NIcon, NGrid, NGi, NStatistic, NDivider
} from 'naive-ui'
import {
  ArrowBackOutline, CheckmarkCircleOutline, CloseCircleOutline,
  SparklesOutline, RibbonOutline, CreateOutline
} from '@vicons/ionicons5'
import { getTask, submitTask, type TaskInfo, type SubmissionResult } from '@/api/task'
import confetti from 'canvas-confetti'

const route = useRoute()
const router = useRouter()

const taskId = computed(() => route.params.taskId as string)

const loading = ref(true)
const submitting = ref(false)
const task = ref<TaskInfo | null>(null)
const loadError = ref('')

const content = ref('')
const attachmentUrls = ref<string[]>([])

const reviewResult = ref<SubmissionResult | null>(null)

const statusTagType = computed(() => {
  if (!task.value) return 'default'
  const map: Record<string, 'default' | 'success' | 'warning' | 'info'> = {
    pending: 'default',
    submitted: 'warning',
    passed: 'success',
    failed: 'error'
  }
  return map[task.value.status] || 'default'
})

const statusLabel = computed(() => {
  if (!task.value) return ''
  const map: Record<string, string> = {
    pending: '待提交',
    submitted: '已提交',
    passed: '已通过',
    failed: '未通过'
  }
  return map[task.value.status] || task.value.status
})

const canSubmit = computed(() => content.value.trim().length > 0 && !submitting.value)

async function loadTask() {
  loading.value = true
  loadError.value = ''
  try {
    task.value = await getTask(taskId.value)
  } catch (e: unknown) {
    loadError.value = (e as Error).message || '加载任务失败'
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  reviewResult.value = null
  try {
    const result = await submitTask(taskId.value, {
      content: content.value,
      attachmentUrls: attachmentUrls.value.length > 0 ? attachmentUrls.value : undefined
    })
    reviewResult.value = result
    // 更新本地任务状态
    if (task.value) {
      task.value.status = result.taskStatus
      task.value.completedAt = result.reviewedAt
    }
    // 通过时触发庆祝动画
    if (result.passed) {
      triggerCelebration()
    }
    window.$message?.success(result.passed ? '审核通过！' : '审核未通过，请根据反馈修改后重新提交')
  } catch {
    // 错误已由 request.ts 拦截器处理
  } finally {
    submitting.value = false
  }
}

function handleResubmit() {
  reviewResult.value = null
  content.value = ''
  attachmentUrls.value = []
}

/** 任务通过时撒花庆祝（canvas-confetti 三波发射） */
function triggerCelebration() {
  confetti({ particleCount: 80, spread: 70, origin: { x: 0.2, y: 0.6 } })
  setTimeout(() => confetti({ particleCount: 80, spread: 70, origin: { x: 0.8, y: 0.6 } }), 150)
  setTimeout(() => confetti({ particleCount: 50, spread: 100, origin: { x: 0.5, y: 0.5 } }), 300)
}

function goBack() {
  router.back()
}

onMounted(loadTask)
</script>

<template>
  <div class="task-submit-page">
    <!-- 顶部导航 -->
    <n-space justify="space-between" align="center" style="margin-bottom: 16px">
      <n-button quaternary @click="goBack">
        <template #icon><n-icon :component="ArrowBackOutline" /></template>
        返回
      </n-button>
      <n-text depth="3" style="font-size: 13px">提交任务</n-text>
    </n-space>

    <!-- 加载中 -->
    <n-spin v-if="loading" size="large" style="display: flex; justify-content: center; padding: 80px 0" />

    <!-- 加载失败 -->
    <n-result
      v-else-if="loadError"
      status="error"
      title="加载失败"
      :description="loadError"
    >
      <template #footer>
        <n-button @click="goBack">返回</n-button>
      </template>
    </n-result>

    <!-- 主内容 -->
    <template v-else-if="task">
      <n-grid :cols="1" :x-gap="16" responsive="screen">
        <n-gi>
          <!-- 任务信息卡片 -->
          <n-card :bordered="false" class="task-card" size="medium">
            <template #header>
              <n-space align="center">
                <n-icon :component="Sparkles" color="#18a058" />
                <span>{{ task.title || '未命名任务' }}</span>
              </n-space>
            </template>
            <template #header-extra>
              <n-tag :type="statusTagType" round size="small">{{ statusLabel }}</n-tag>
            </template>

            <n-space vertical size="large">
              <div>
                <n-text depth="3" style="font-size: 13px; display: block; margin-bottom: 6px">任务描述</n-text>
                <n-text>{{ task.description }}</n-text>
              </div>

              <div v-if="task.verificationCriteria">
                <n-text depth="3" style="font-size: 13px; display: block; margin-bottom: 6px">验证标准</n-text>
                <n-text>{{ task.verificationCriteria }}</n-text>
              </div>

              <n-space size="large">
                <n-statistic v-if="task.dueDate" label="截止日期" :value="task.dueDate" />
                <n-statistic v-if="task.dayNumber" label="第几天" :value="task.dayNumber" />
              </n-space>
            </n-space>
          </n-card>
        </n-gi>

        <!-- 已通过提示 -->
        <n-gi v-if="task.status === 'passed' && !reviewResult">
          <n-card :bordered="false" size="medium">
            <n-result status="success" title="任务已通过" description="该任务已通过审核，无需重复提交。">
              <template #footer>
                <n-button @click="goBack">返回项目</n-button>
              </template>
            </n-result>
          </n-card>
        </n-gi>

        <!-- 提交表单 -->
        <n-gi v-else-if="!reviewResult">
          <n-card title="提交学习总结" :bordered="false" size="medium">
            <template #header-extra>
              <n-text depth="3" style="font-size: 12px">支持 Markdown 格式</n-text>
            </template>
            <n-space vertical size="large">
              <div>
                <n-text depth="3" style="font-size: 13px; display: block; margin-bottom: 8px">学习总结 *</n-text>
                <n-input
                  v-model:value="content"
                  type="textarea"
                  placeholder="写下你的学习总结、实践过程和思考..."
                  :autosize="{ minRows: 6, maxRows: 20 }"
                  :disabled="submitting"
                />
                <n-text depth="3" style="font-size: 12px; display: block; margin-top: 4px">
                  {{ content.length }} / 10000 字符
                </n-text>
              </div>

              <div>
                <n-text depth="3" style="font-size: 13px; display: block; margin-bottom: 8px">附件链接（可选）</n-text>
                <n-dynamic-tags
                  v-model:value="attachmentUrls"
                  :max="10"
                  :disabled="submitting"
                />
              </div>

              <n-space justify="end">
                <n-button
                  type="primary"
                  size="large"
                  :loading="submitting"
                  :disabled="!canSubmit"
                  @click="handleSubmit"
                >
                  <template #icon><n-icon :component="CreateOutline" /></template>
                  提交审核
                </n-button>
              </n-space>
            </n-space>
          </n-card>
        </n-gi>

        <!-- AI 审核结果 -->
        <n-gi v-if="reviewResult">
          <n-card title="AI 审核结果" :bordered="false" size="medium">
            <template #header-extra>
              <n-tag size="small" :type="reviewResult.aiModel === 'mock' ? 'default' : 'info'">
                {{ reviewResult.aiModel === 'mock' ? 'Mock 模式' : reviewResult.aiModel }}
              </n-tag>
            </template>

            <div :class="['review-result', reviewResult.passed ? 'review-passed' : 'review-failed']">
              <n-space vertical size="large">
                <!-- 通过/未通过标志 -->
                <div class="review-status">
                  <n-icon
                    :component="reviewResult.passed ? CheckmarkCircleOutline : CloseCircleOutline"
                    :color="reviewResult.passed ? '#18a058' : '#f0a020'"
                    :size="48"
                  />
                  <n-text strong style="font-size: 20px">
                    {{ reviewResult.passed ? '审核通过！' : '审核未通过' }}
                  </n-text>
                </div>

                <!-- 评分 -->
                <div class="score-section">
                  <n-space align="center" size="large">
                    <n-statistic label="评分" :value="`${reviewResult.score} / 10`" />
                    <n-icon :component="RibbonOutline" :size="32" :color="reviewResult.passed ? '#18a058' : '#f0a020'" />
                  </n-space>
                </div>

                <n-divider />

                <!-- AI 评语 -->
                <div>
                  <n-text depth="3" style="font-size: 13px; display: block; margin-bottom: 8px">AI 评语</n-text>
                  <div class="feedback-content">{{ reviewResult.feedback }}</div>
                </div>

                <!-- 知识库条目提示 -->
                <n-alert v-if="reviewResult.knowledgeEntryId" type="success" :bordered="false">
                  本次提交已自动归档到知识库，可在「知识库」中查看。
                </n-alert>

                <!-- 操作按钮 -->
                <n-space justify="center">
                  <n-button v-if="!reviewResult.passed" type="primary" size="large" @click="handleResubmit">
                    重新提交
                  </n-button>
                  <n-button size="large" @click="goBack">返回项目</n-button>
                </n-space>
              </n-space>
            </div>
          </n-card>
        </n-gi>
      </n-grid>
    </template>
  </div>
</template>

<style scoped lang="scss">
.task-submit-page {
  max-width: 800px;
  margin: 0 auto;
  position: relative;
}

.task-card {
  margin-bottom: 16px;
}

.review-result {
  padding: 16px 0;
}

.review-status {
  display: flex;
  align-items: center;
  gap: 16px;
}

.score-section {
  padding: 8px 0;
}

.feedback-content {
  padding: 16px;
  background: rgba(128, 128, 128, 0.05);
  border-radius: 8px;
  line-height: 1.8;
  font-size: 14px;
}

.review-passed {
  // 通过时的样式
}

.review-failed {
  // 未通过时的样式
}
</style>
