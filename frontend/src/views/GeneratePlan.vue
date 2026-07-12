<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NCard, NButton, NSpace, NText, NTag, NSteps, NStep, NInputNumber,
  NUpload, NUploadDragger, NSwitch, NAlert, NSpin, NResult, NCollapse,
  NCollapseItem, NIcon, NGrid, NGi, NStatistic
} from 'naive-ui'
import type { UploadFileInfo } from 'naive-ui'
import {
  ArrowBackOutline, SparklesOutline, CloudUploadOutline,
  SearchOutline, CheckmarkCircleOutline,
  TimeOutline, LayersOutline, RefreshOutline, EnterOutline
} from '@vicons/ionicons5'
import { getProject, type Project } from '@/api/project'
import { generatePlan, type GeneratePlanResult } from '@/api/plan'
import { getAiConfig, type AiConfigResponse } from '@/api/aiConfig'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => route.params.id as string)
const currentStep = ref(1)
const loading = ref(true)

// 项目数据
const project = ref<Project | null>(null)

// AI 配置
const aiConfig = ref<AiConfigResponse | null>(null)
const hasDeepseekKey = computed(() => aiConfig.value?.hasDeepseekKey ?? false)
const hasSearchKey = computed(() => aiConfig.value?.hasSearchKey ?? false)

// 表单数据
const targetDays = ref(14)
const uploadedFiles = ref<UploadFileInfo[]>([])
const useWebSearch = ref(false)

// 生成状态
const generating = ref(false)
const generateError = ref('')
const result = ref<GeneratePlanResult | null>(null)

// 计算属性
const selectedFiles = computed(() =>
  uploadedFiles.value
    .filter((f) => f.file && f.status !== 'removed')
    .map((f) => f.file!)
)

const canProceedStep1 = computed(() => targetDays.value > 0 && targetDays.value <= 90)
const canGenerate = computed(() => {
  if (!hasDeepseekKey.value) return false
  if (useWebSearch.value && !hasSearchKey.value) return false
  return true
})

// 按阶段分组的任务（用于结果预览）
const tasksByPhase = computed(() => {
  if (!result.value) return []
  return result.value.phases.map((phase) => ({
    phaseName: phase.name,
    phaseObjective: phase.objective || '',
    tasks: result.value!.tasks.filter((t) => t.phaseId === phase.id)
  }))
})

// 方法
async function loadData() {
  loading.value = true
  try {
    const [proj, config] = await Promise.all([
      getProject(projectId.value),
      getAiConfig()
    ])
    project.value = proj
    aiConfig.value = config
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}

function handleNext() {
  currentStep.value++
}

function handlePrev() {
  currentStep.value--
}

async function handleGenerate() {
  currentStep.value = 4
  generating.value = true
  generateError.value = ''
  result.value = null

  try {
    result.value = await generatePlan(
      projectId.value,
      selectedFiles.value,
      useWebSearch.value,
      targetDays.value,
      true
    )
    currentStep.value = 5
  } catch (e: unknown) {
    generateError.value = (e as Error).message || 'AI 生成失败'
  } finally {
    generating.value = false
  }
}

function handleRegenerate() {
  currentStep.value = 1
  result.value = null
  generateError.value = ''
}

function handleViewProject() {
  router.push({ name: 'project-detail', params: { id: projectId.value } })
}

function goBack() {
  router.push({ name: 'project-detail', params: { id: projectId.value } })
}

onMounted(loadData)
</script>

<template>
  <div class="generate-plan-page">
    <!-- 顶部导航 -->
    <n-space justify="space-between" align="center" style="margin-bottom: 20px">
      <n-button quaternary @click="goBack">
        <template #icon><n-icon :component="ArrowBackOutline" /></template>
        返回项目
      </n-button>
      <n-space align="center" size="small">
        <n-icon :component="SparklesOutline" :size="20" color="#18a058" />
        <n-text strong style="font-size: 16px">AI 生成学习路线</n-text>
      </n-space>
    </n-space>

    <!-- 加载中 -->
    <n-spin v-if="loading" size="large" style="display: flex; justify-content: center; padding: 80px 0" />

    <template v-else-if="project">
      <!-- 项目信息 -->
      <n-card :bordered="false" style="margin-bottom: 20px; border-radius: 10px">
        <n-space align="center" size="small">
          <n-text strong style="font-size: 16px">{{ project.name }}</n-text>
          <n-tag size="small" round>{{ project.status || '进行中' }}</n-tag>
        </n-space>
        <n-text v-if="project.goal" depth="3" style="font-size: 13px; display: block; margin-top: 6px">
          🎯 {{ project.goal }}
        </n-text>
        <n-text v-if="project.description" depth="2" style="font-size: 13px; display: block; margin-top: 4px">
          {{ project.description }}
        </n-text>
      </n-card>

      <!-- 未配置 DeepSeek Key 警告 -->
      <n-alert v-if="!hasDeepseekKey" type="warning" style="margin-bottom: 20px; border-radius: 10px" :bordered="false">
        未配置 DeepSeek API Key，无法使用 AI 生成功能。请先前往
        <n-button text type="info" @click="router.push({ name: 'settings' })">设置页面</n-button>
        配置。
      </n-alert>

      <!-- 步骤条 -->
      <n-steps :current="currentStep" size="small" style="margin-bottom: 28px">
        <n-step title="目标时长" description="设置学习天数" />
        <n-step title="上传资料" description="可选，提供参考资料" />
        <n-step title="搜索增强" description="可选，联网搜索" />
        <n-step title="AI 生成" description="等待 AI 分析" />
        <n-step title="结果预览" description="查看生成路线" />
      </n-steps>

      <!-- Step 1: 目标时长 -->
      <n-card v-if="currentStep === 1" :bordered="false" style="border-radius: 10px">
        <n-space vertical size="large">
          <n-space align="center">
            <n-icon :component="TimeOutline" :size="20" color="#18a058" />
            <n-text strong style="font-size: 15px">目标学习天数</n-text>
          </n-space>
          <n-text depth="2" style="font-size: 13px; line-height: 1.6">
            设置你希望在多少天内完成这个学习项目。AI 会根据天数合理分配每日任务。
          </n-text>
          <n-space align="center" size="large">
            <n-input-number
              v-model:value="targetDays"
              :min="1"
              :max="90"
              :step="1"
              style="width: 140px"
              size="large"
            />
            <n-text depth="3">天（1-90）</n-text>
          </n-space>
          <n-text depth="3" style="font-size: 12px">
            当前每日学习时长：{{ project.dailyHours || 2 }} 小时
          </n-text>
        </n-space>

        <template #footer>
          <n-space justify="end">
            <n-button
              type="primary"
              :disabled="!canProceedStep1"
              @click="handleNext"
            >
              下一步
            </n-button>
          </n-space>
        </template>
      </n-card>

      <!-- Step 2: 上传资料 -->
      <n-card v-else-if="currentStep === 2" :bordered="false" style="border-radius: 10px">
        <n-space vertical size="large">
          <n-space align="center">
            <n-icon :component="CloudUploadOutline" :size="20" color="#18a058" />
            <n-text strong style="font-size: 15px">上传学习资料</n-text>
          </n-space>
          <n-text depth="2" style="font-size: 13px; line-height: 1.6">
            上传 .md 或 .txt 格式的学习资料，AI 会分析内容并融入任务设计。最多 10 个文件，每个文件不超过 4,000 字。
          </n-text>
          <n-upload
            v-model:file-list="uploadedFiles"
            :default-upload="false"
            accept=".md,.txt"
            :max="10"
            multiple
          >
            <n-upload-dragger style="padding: 30px">
              <n-space vertical align="center" size="small">
                <n-icon :component="CloudUploadOutline" :size="40" depth="3" />
                <n-text>点击或拖拽文件到此处</n-text>
                <n-text depth="3" style="font-size: 12px">支持 .md、.txt 格式，最多 10 个文件</n-text>
              </n-space>
            </n-upload-dragger>
          </n-upload>
          <n-text v-if="selectedFiles.length > 0" depth="3" style="font-size: 12px">
            已选择 {{ selectedFiles.length }} 个文件
          </n-text>
        </n-space>

        <template #footer>
          <n-space justify="space-between">
            <n-button @click="handlePrev">上一步</n-button>
            <n-button type="primary" @click="handleNext">下一步</n-button>
          </n-space>
        </template>
      </n-card>

      <!-- Step 3: 搜索开关 -->
      <n-card v-else-if="currentStep === 3" :bordered="false" style="border-radius: 10px">
        <n-space vertical size="large">
          <n-space align="center">
            <n-icon :component="SearchOutline" :size="20" color="#18a058" />
            <n-text strong style="font-size: 15px">联网搜索增强</n-text>
          </n-space>
          <n-text depth="2" style="font-size: 13px; line-height: 1.6">
            开启后，AI 会先根据你的学习目标生成搜索关键词，通过 Bing 搜索最新资料，再结合搜索结果生成更丰富的学习路线。
          </n-text>

          <n-alert v-if="!hasSearchKey" type="info" :bordered="false">
            未配置 Bing 搜索 API Key。开启搜索需要先在设置页配置搜索 Key。你可以跳过搜索直接生成。
          </n-alert>

          <n-space align="center" size="large">
            <n-text>启用联网搜索</n-text>
            <n-switch
              v-model:value="useWebSearch"
              :disabled="!hasSearchKey"
            />
            <n-tag v-if="useWebSearch" size="small" type="success" round>已开启</n-tag>
            <n-tag v-else size="small" round>未开启</n-tag>
          </n-space>
        </n-space>

        <template #footer>
          <n-space justify="space-between">
            <n-button @click="handlePrev">上一步</n-button>
            <n-button
              type="primary"
              :disabled="!canGenerate"
              @click="handleGenerate"
            >
              <template #icon><n-icon :component="SparklesOutline" /></template>
              开始生成
            </n-button>
          </n-space>
        </template>
      </n-card>

      <!-- Step 4: 生成中 / 生成失败 -->
      <n-card v-else-if="currentStep === 4" :bordered="false" style="border-radius: 10px">
        <div v-if="generating" style="text-align: center; padding: 60px 0">
          <n-spin size="large" />
          <n-text strong style="font-size: 16px; display: block; margin-top: 20px">
            AI 正在分析资料并生成学习路线...
          </n-text>
          <n-text depth="3" style="font-size: 13px; display: block; margin-top: 8px">
            这可能需要 30-60 秒，请耐心等待
          </n-text>
        </div>

        <n-result
          v-else-if="generateError"
          status="error"
          title="生成失败"
          :description="generateError"
        >
          <template #footer>
            <n-space justify="center">
              <n-button @click="currentStep = 3">返回调整</n-button>
              <n-button type="primary" @click="handleGenerate">重试</n-button>
            </n-space>
          </template>
        </n-result>
      </n-card>

      <!-- Step 5: 结果预览 -->
      <template v-else-if="currentStep === 5 && result">
        <!-- 统计卡片 -->
        <n-grid :cols="2" :x-gap="16" style="margin-bottom: 20px">
          <n-gi>
            <n-card :bordered="false" style="border-radius: 10px">
              <n-statistic label="生成阶段数" :value="result.phaseCount">
                <template #prefix><n-icon :component="LayersOutline" color="#18a058" /></template>
              </n-statistic>
            </n-card>
          </n-gi>
          <n-gi>
            <n-card :bordered="false" style="border-radius: 10px">
              <n-statistic label="生成任务数" :value="result.taskCount">
                <template #prefix><n-icon :component="CheckmarkCircleOutline" color="#18a058" /></template>
              </n-statistic>
            </n-card>
          </n-gi>
        </n-grid>

        <!-- 阶段和任务预览 -->
        <n-card title="学习路线预览" :bordered="false" style="border-radius: 10px; margin-bottom: 20px">
          <n-collapse :default-expanded-keys="tasksByPhase.map((_, i) => String(i))">
            <n-collapse-item
              v-for="(group, idx) in tasksByPhase"
              :key="idx"
              :name="String(idx)"
            >
              <template #header>
                <n-space align="center" size="small">
                  <n-text strong>{{ group.phaseName }}</n-text>
                  <n-tag size="tiny" round>{{ group.tasks.length }} 个任务</n-tag>
                </n-space>
              </template>

              <n-text v-if="group.phaseObjective" depth="3" style="font-size: 13px; display: block; margin-bottom: 12px">
                📌 {{ group.phaseObjective }}
              </n-text>

              <n-space vertical size="small">
                <n-card
                  v-for="task in group.tasks"
                  :key="task.id"
                  size="small"
                  :bordered="true"
                  style="border-radius: 8px"
                >
                  <n-space align="center" size="small" style="margin-bottom: 4px">
                    <n-tag size="tiny" type="info" round>第 {{ task.dayNumber }} 天</n-tag>
                    <n-text strong style="font-size: 14px">{{ task.title }}</n-text>
                  </n-space>
                  <n-text depth="2" style="font-size: 13px; line-height: 1.5; display: block">
                    {{ task.description }}
                  </n-text>
                  <n-text v-if="task.verificationCriteria" depth="3" style="font-size: 12px; display: block; margin-top: 6px">
                    ✅ 验证标准：{{ task.verificationCriteria }}
                  </n-text>
                </n-card>
              </n-space>
            </n-collapse-item>
          </n-collapse>
        </n-card>

        <!-- 操作按钮 -->
        <n-space justify="center" size="large">
          <n-button @click="handleRegenerate">
            <template #icon><n-icon :component="RefreshOutline" /></template>
            重新生成
          </n-button>
          <n-button type="primary" @click="handleViewProject">
            <template #icon><n-icon :component="EnterOutline" /></template>
            查看项目
          </n-button>
        </n-space>
      </template>
    </template>
  </div>
</template>

<style scoped lang="scss">
.generate-plan-page {
  max-width: 800px;
  margin: 0 auto;
}
</style>
