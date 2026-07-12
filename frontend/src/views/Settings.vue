<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  NCard, NTabs, NTabPane, NForm, NFormItem, NInput, NButton, NSwitch,
  NSelect, NAlert, NSpace, NText, NTag, NSpin, NResult, NIcon, NGrid, NGi,
  NTimePicker
} from 'naive-ui'
import {
  CheckmarkCircleOutline, CloseCircleOutline, EyeOutline, EyeOffOutline,
  ShieldCheckmarkOutline, SparklesOutline, SearchOutline, SaveOutline, FlaskOutline,
  NotificationsOutline, MailOutline, TimeOutline
} from '@vicons/ionicons5'
import {
  getAiConfig, saveAiConfig, testAiConfig,
  type AiConfigResponse, type AiConfigRequest
} from '@/api/aiConfig'
import {
  getReminder, saveReminder,
  type ReminderResponse, type ReminderRequest
} from '@/api/reminder'

// ============ 状态 ============
const loading = ref(true)
const saving = ref(false)
const testingDeepseek = ref(false)
const testingSearch = ref(false)

const config = reactive<AiConfigResponse>({
  deepseekApiKey: '',
  hasDeepseekKey: false,
  searchApiKey: '',
  hasSearchKey: false,
  deepseekBaseUrl: 'https://api.deepseek.com/v1',
  deepseekModel: 'deepseek-chat',
  searchProvider: 'bing',
  localMode: false,
  embeddingModel: 'bge-large-zh'
})

// 表单输入（Key 字段单独管理：留空表示保留已有）
const deepseekKeyInput = ref('')
const searchKeyInput = ref('')
const showDeepseekKey = ref(false)
const showSearchKey = ref(false)

// 测试结果
const deepseekTestResult = ref<{ success: boolean; message: string } | null>(null)
const searchTestResult = ref<{ success: boolean; message: string } | null>(null)

// ============ 选项 ============
const modelOptions = [
  { label: 'deepseek-chat（通用对话）', value: 'deepseek-chat' },
  { label: 'deepseek-reasoner（推理模型）', value: 'deepseek-reasoner' },
  { label: '自定义（手动输入）', value: '__custom__' }
]
const customModelInput = ref('')
const isCustomModel = computed(() =>
  config.deepseekModel !== 'deepseek-chat' && config.deepseekModel !== 'deepseek-reasoner'
)
const modelSelectValue = computed(() => isCustomModel.value ? '__custom__' : config.deepseekModel)

const searchProviderOptions = [
  { label: 'Bing Web Search v7', value: 'bing' }
]

// ============ 计算属性 ============
const deepseekKeyPlaceholder = computed(() =>
  config.hasDeepseekKey ? `已配置（${config.deepseekApiKey}），留空则保留` : '请输入 DeepSeek API Key'
)
const searchKeyPlaceholder = computed(() =>
  config.hasSearchKey ? `已配置（${config.searchApiKey}），留空则保留` : '请输入搜索 API Key'
)

// 本地模式下 Key 输入禁用（因为不上传）
const keyInputDisabled = computed(() => config.localMode)

// ============ 方法 ============
async function loadConfig() {
  loading.value = true
  try {
    const data = await getAiConfig()
    Object.assign(config, data)
    deepseekKeyInput.value = ''
    searchKeyInput.value = ''
    if (isCustomModel.value) {
      customModelInput.value = config.deepseekModel
    }
  } finally {
    loading.value = false
  }
}

function handleModelChange(value: string) {
  if (value === '__custom__') {
    if (!customModelInput.value) customModelInput.value = ''
    config.deepseekModel = customModelInput.value
  } else {
    config.deepseekModel = value
    customModelInput.value = ''
  }
}

function handleCustomModelInput(value: string) {
  customModelInput.value = value
  config.deepseekModel = value
}

function handleLocalModeChange(enabled: boolean) {
  config.localMode = enabled
  if (enabled) {
    // 开启本地模式时清空输入，避免误传
    deepseekKeyInput.value = ''
    searchKeyInput.value = ''
    deepseekTestResult.value = null
    searchTestResult.value = null
  }
}

async function handleSave() {
  saving.value = true
  try {
    const payload: AiConfigRequest = {
      deepseekBaseUrl: config.deepseekBaseUrl,
      deepseekModel: config.deepseekModel,
      searchProvider: config.searchProvider,
      localMode: config.localMode,
      embeddingModel: config.embeddingModel
    }
    // 非本地模式下，有输入才传 Key
    if (!config.localMode) {
      if (deepseekKeyInput.value.trim()) payload.deepseekApiKey = deepseekKeyInput.value.trim()
      if (searchKeyInput.value.trim()) payload.searchApiKey = searchKeyInput.value.trim()
    }
    const data = await saveAiConfig(payload)
    Object.assign(config, data)
    deepseekKeyInput.value = ''
    searchKeyInput.value = ''
    window.$message?.success('AI 配置已保存')
  } finally {
    saving.value = false
  }
}

async function handleTestDeepseek() {
  testingDeepseek.value = true
  deepseekTestResult.value = null
  try {
    const result = await testAiConfig({
      provider: 'deepseek',
      // 优先用输入框的临时 Key，没输入则不传（后端用已存储的）
      apiKey: deepseekKeyInput.value.trim() || undefined,
      baseUrl: config.deepseekBaseUrl || undefined
    })
    deepseekTestResult.value = { success: result.success, message: `${result.message}（${result.latencyMs}ms）` }
    if (result.success) {
      window.$message?.success('DeepSeek Key 验证通过')
    } else {
      window.$message?.warning(result.message)
    }
  } finally {
    testingDeepseek.value = false
  }
}

async function handleTestSearch() {
  testingSearch.value = true
  searchTestResult.value = null
  try {
    const result = await testAiConfig({
      provider: 'search',
      apiKey: searchKeyInput.value.trim() || undefined
    })
    searchTestResult.value = { success: result.success, message: result.message }
    if (result.success) {
      window.$message?.success('搜索 API 验证通过')
    } else {
      window.$message?.info(result.message)
    }
  } finally {
    testingSearch.value = false
  }
}

// ============ 提醒设置 ============
const reminderLoading = ref(false)
const reminderSaving = ref(false)
const reminder = reactive<ReminderResponse>({
  email: '',
  reminderTime: null,
  timezone: 'Asia/Shanghai',
  enabled: false,
  lastSentAt: null,
  createdAt: null,
  updatedAt: null
})

// NTimePicker 用时间戳，需要转换
const reminderTimeTs = ref<number | null>(null)

const timezoneOptions = [
  { label: '(UTC+08:00) 北京/上海/香港', value: 'Asia/Shanghai' },
  { label: '(UTC+09:00) 东京/首尔', value: 'Asia/Tokyo' },
  { label: '(UTC+07:00) 曼谷/雅加达', value: 'Asia/Bangkok' },
  { label: '(UTC+00:00) 伦敦/都柏林', value: 'Europe/London' },
  { label: '(UTC-05:00) 纽约/华盛顿', value: 'America/New_York' },
  { label: '(UTC-08:00) 洛杉矶/旧金山', value: 'America/Los_Angeles' }
]

function parseTimeToTs(timeStr: string | null): number | null {
  if (!timeStr) return null
  // timeStr 格式 "HH:mm:ss" 或 "HH:mm"
  const [h, m] = timeStr.split(':').map(Number)
  const d = new Date()
  d.setHours(h || 0, m || 0, 0, 0)
  return d.getTime()
}

function tsToTimeStr(ts: number | null): string | null {
  if (ts === null) return null
  const d = new Date(ts)
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${h}:${m}:00`
}

function formatLastSent(dateStr: string | null): string {
  if (!dateStr) return '从未发送'
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

async function loadReminder() {
  reminderLoading.value = true
  try {
    const data = await getReminder()
    Object.assign(reminder, data)
    reminderTimeTs.value = parseTimeToTs(data.reminderTime)
  } finally {
    reminderLoading.value = false
  }
}

async function handleSaveReminder() {
  if (!reminderTimeTs.value) {
    window.$message?.warning('请选择提醒时间')
    return
  }
  reminderSaving.value = true
  try {
    const payload: ReminderRequest = {
      email: reminder.email,
      reminderTime: tsToTimeStr(reminderTimeTs.value)!,
      timezone: reminder.timezone,
      enabled: reminder.enabled
    }
    const data = await saveReminder(payload)
    Object.assign(reminder, data)
    window.$message?.success('提醒设置已保存')
  } finally {
    reminderSaving.value = false
  }
}

onMounted(() => {
  loadConfig()
  loadReminder()
})
</script>

<template>
  <n-spin v-if="loading" size="large" style="display: flex; justify-content: center; padding: 80px 0" />

  <n-card v-else title="设置" :bordered="false">
    <n-tabs type="line" animated>
      <!-- ============ AI 配置 ============ -->
      <n-tab-pane name="ai" tab="AI 配置">
        <n-space vertical size="large">
          <!-- 本地模式开关 + 风险提示 -->
          <n-alert :type="config.localMode ? 'warning' : 'info'" :bordered="false" style="border-radius: 8px">
            <template #header>
              <n-space align="center" size="small">
                <n-icon :component="ShieldCheckmarkOutline" />
                <span>本地模式</span>
                <n-switch
                  :value="config.localMode"
                  size="small"
                  @update:value="handleLocalModeChange"
                />
              </n-space>
            </template>
            <n-text v-if="!config.localMode" depth="3" style="font-size: 13px">
              关闭时：API Key 经 AES-256-GCM 加密后存储在服务端，可在多设备共享，支持服务端 AI 审核与路线生成。
            </n-text>
            <n-text v-else style="font-size: 13px; color: #d03050">
              开启时：API Key 不会上传服务端，仅存在当前浏览器中（换设备需重新配置）。
              服务端 AI 审核、路线生成等需要调用 Key 的功能将降级为 Mock 或不可用。
            </n-text>
          </n-alert>

          <!-- DeepSeek 配置 -->
          <n-card title="DeepSeek 配置" size="small" :bordered="true" style="border-radius: 8px">
            <template #header-extra>
              <n-tag v-if="config.hasDeepseekKey && !config.localMode" type="success" size="small" round>
                <template #icon><n-icon :component="CheckmarkCircleOutline" /></template>
                已配置
              </n-tag>
              <n-tag v-else-if="config.localMode" type="warning" size="small" round>本地模式</n-tag>
              <n-tag v-else type="default" size="small" round>未配置</n-tag>
            </template>

            <n-form label-placement="top" :show-feedback="true">
              <n-grid :cols="1" :x-gap="16" responsive="screen" item-responsive>
                <!-- API Key -->
                <n-gi>
                  <n-form-item label="DeepSeek API Key">
                    <n-input
                      v-model:value="deepseekKeyInput"
                      :type="showDeepseekKey ? 'text' : 'password'"
                      :placeholder="deepseekKeyPlaceholder"
                      :disabled="keyInputDisabled"
                      clearable
                    >
                      <template #prefix>
                        <n-icon :component="SparklesOutline" />
                      </template>
                      <template #suffix>
                        <n-button
                          text
                          :disabled="keyInputDisabled"
                          @click="showDeepseekKey = !showDeepseekKey"
                        >
                          <n-icon :component="showDeepseekKey ? EyeOffOutline : EyeOutline" />
                        </n-button>
                      </template>
                    </n-input>
                  </n-form-item>
                </n-gi>

                <!-- Base URL -->
                <n-gi>
                  <n-form-item label="Base URL">
                    <n-input
                      v-model:value="config.deepseekBaseUrl"
                      placeholder="https://api.deepseek.com/v1"
                    />
                  </n-form-item>
                </n-gi>

                <!-- 模型选择 -->
                <n-gi>
                  <n-form-item label="模型">
                    <n-space vertical style="width: 100%" size="small">
                      <n-select
                        :value="modelSelectValue"
                        :options="modelOptions"
                        @update:value="handleModelChange"
                      />
                      <n-input
                        v-if="isCustomModel"
                        v-model:value="customModelInput"
                        placeholder="输入自定义模型名称"
                        @update:value="handleCustomModelInput"
                      >
                        <template #prefix><n-icon :component="SparklesOutline" /></template>
                      </n-input>
                    </n-space>
                  </n-form-item>
                </n-gi>
              </n-grid>

              <!-- 测试按钮 -->
              <n-space align="center" justify="space-between" style="margin-top: 8px">
                <n-button
                  type="default"
                  :loading="testingDeepseek"
                  :disabled="config.localMode"
                  @click="handleTestDeepseek"
                >
                  <template #icon><n-icon :component="FlaskOutline" /></template>
                  测试连接
                </n-button>
                <n-text v-if="config.localMode" depth="3" style="font-size: 12px">
                  本地模式下请在浏览器中自行测试
                </n-text>
              </n-space>

              <!-- 测试结果 -->
              <n-alert
                v-if="deepseekTestResult"
                :type="deepseekTestResult.success ? 'success' : 'error'"
                :bordered="false"
                style="margin-top: 12px"
                closable
                @close="deepseekTestResult = null"
              >
                <template #icon>
                  <n-icon :component="deepseekTestResult.success ? CheckmarkCircleOutline : CloseCircleOutline" />
                </template>
                {{ deepseekTestResult.message }}
              </n-alert>
            </n-form>
          </n-card>

          <!-- 搜索 API 配置 -->
          <n-card title="搜索 API 配置" size="small" :bordered="true" style="border-radius: 8px">
            <template #header-extra>
              <n-tag v-if="config.hasSearchKey && !config.localMode" type="success" size="small" round>
                <template #icon><n-icon :component="CheckmarkCircleOutline" /></template>
                已配置
              </n-tag>
              <n-tag v-else-if="config.localMode" type="warning" size="small" round>本地模式</n-tag>
              <n-tag v-else type="default" size="small" round>未配置</n-tag>
            </template>

            <n-form label-placement="top">
              <n-grid :cols="1" :x-gap="16" responsive="screen" item-responsive>
                <!-- 搜索引擎 -->
                <n-gi>
                  <n-form-item label="搜索引擎">
                    <n-select
                      v-model:value="config.searchProvider"
                      :options="searchProviderOptions"
                    />
                  </n-form-item>
                </n-gi>

                <!-- API Key -->
                <n-gi>
                  <n-form-item label="搜索 API Key">
                    <n-input
                      v-model:value="searchKeyInput"
                      :type="showSearchKey ? 'text' : 'password'"
                      :placeholder="searchKeyPlaceholder"
                      :disabled="keyInputDisabled"
                      clearable
                    >
                      <template #prefix>
                        <n-icon :component="SearchOutline" />
                      </template>
                      <template #suffix>
                        <n-button
                          text
                          :disabled="keyInputDisabled"
                          @click="showSearchKey = !showSearchKey"
                        >
                          <n-icon :component="showSearchKey ? EyeOffOutline : EyeOutline" />
                        </n-button>
                      </template>
                    </n-input>
                  </n-form-item>
                </n-gi>
              </n-grid>

              <n-space align="center" justify="space-between" style="margin-top: 8px">
                <n-button
                  type="default"
                  :loading="testingSearch"
                  :disabled="config.localMode"
                  @click="handleTestSearch"
                >
                  <template #icon><n-icon :component="FlaskOutline" /></template>
                  测试连接
                </n-button>
                <n-text depth="3" style="font-size: 12px">
                  搜索功能将在阶段二启用
                </n-text>
              </n-space>

              <n-alert
                v-if="searchTestResult"
                :type="searchTestResult.success ? 'success' : 'info'"
                :bordered="false"
                style="margin-top: 12px"
                closable
                @close="searchTestResult = null"
              >
                {{ searchTestResult.message }}
              </n-alert>
            </n-form>
          </n-card>

          <!-- 保存按钮 -->
          <n-space justify="end">
            <n-button
              type="primary"
              size="large"
              :loading="saving"
              @click="handleSave"
            >
              <template #icon><n-icon :component="SaveOutline" /></template>
              保存配置
            </n-button>
          </n-space>
        </n-space>
      </n-tab-pane>

      <!-- ============ 提醒设置 ============ -->
      <n-tab-pane name="reminder" tab="提醒设置">
        <n-spin v-if="reminderLoading" size="medium" style="display: flex; justify-content: center; padding: 40px 0" />
        <n-space v-else vertical size="large">
          <!-- 开关 + 说明 -->
          <n-alert :type="reminder.enabled ? 'success' : 'default'" :bordered="false" style="border-radius: 8px">
            <template #header>
              <n-space align="center" size="small">
                <n-icon :component="NotificationsOutline" />
                <span>每日邮件提醒</span>
                <n-switch v-model:value="reminder.enabled" size="small" />
              </n-space>
            </template>
            <n-text depth="3" style="font-size: 13px">
              开启后，每天在指定时间向你的邮箱发送今日待完成任务清单和 AI 鼓励语，帮助保持学习节奏。
            </n-text>
          </n-alert>

          <!-- 提醒配置 -->
          <n-card title="提醒配置" size="small" :bordered="true" style="border-radius: 8px">
            <n-form label-placement="top">
              <n-grid :cols="1" :x-gap="16" responsive="screen" item-responsive>
                <!-- 邮箱 -->
                <n-gi>
                  <n-form-item label="接收邮箱">
                    <n-input
                      v-model:value="reminder.email"
                      placeholder="user@example.com"
                    >
                      <template #prefix><n-icon :component="MailOutline" /></template>
                    </n-input>
                  </n-form-item>
                </n-gi>

                <!-- 提醒时间 -->
                <n-gi>
                  <n-form-item label="每日提醒时间">
                    <n-time-picker
                      v-model:value="reminderTimeTs"
                      format="HH:mm"
                      :hours="[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23]"
                      :minutes="[0,15,30,45]"
                      :seconds="[0]"
                      placeholder="选择提醒时间"
                      style="width: 100%"
                    >
                      <template #prefix><n-icon :component="TimeOutline" /></template>
                    </n-time-picker>
                  </n-form-item>
                </n-gi>

                <!-- 时区 -->
                <n-gi>
                  <n-form-item label="时区">
                    <n-select
                      v-model:value="reminder.timezone"
                      :options="timezoneOptions"
                    />
                  </n-form-item>
                </n-gi>
              </n-grid>
            </n-form>
          </n-card>

          <!-- 上次发送时间 -->
          <n-card title="发送记录" size="small" :bordered="true" style="border-radius: 8px">
            <n-space align="center" size="small">
              <n-icon :component="TimeOutline" color="#909399" />
              <n-text depth="3" style="font-size: 13px">上次发送时间：</n-text>
              <n-tag :type="reminder.lastSentAt ? 'success' : 'default'" size="small" round>
                {{ formatLastSent(reminder.lastSentAt) }}
              </n-tag>
            </n-space>
            <n-text depth="3" style="font-size: 12px; display: block; margin-top: 8px">
              每天最多发送一次，当天已发送不会重复发送。请确保邮箱地址正确，否则将发送失败。
            </n-text>
          </n-card>

          <!-- 保存按钮 -->
          <n-space justify="end">
            <n-button
              type="primary"
              size="large"
              :loading="reminderSaving"
              @click="handleSaveReminder"
            >
              <template #icon><n-icon :component="SaveOutline" /></template>
              保存提醒设置
            </n-button>
          </n-space>
        </n-space>
      </n-tab-pane>

      <!-- ============ 个人资料（占位） ============ -->
      <n-tab-pane name="profile" tab="个人资料">
        <n-result status="info" title="个人资料" description="将在后续实现：昵称、头像、时区">
        </n-result>
      </n-tab-pane>
    </n-tabs>
  </n-card>
</template>
