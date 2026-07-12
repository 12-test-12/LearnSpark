<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NCard, NButton, NSpace, NText, NTag, NProgress, NSpin, NResult,
  NIcon, NEmpty, NTimeline, NTimelineItem, NPopconfirm, NGrid, NGi, NStatistic
} from 'naive-ui'
import {
  ArrowBackOutline, AddOutline, CreateOutline, TrashOutline,
  SparklesOutline, TimeOutline, FlagOutline, LayersOutline, CheckmarkDoneOutline
} from '@vicons/ionicons5'
import {
  getProject, listPhases, listTasks, deleteTask, deletePhase,
  type Project, type Phase, type Task, type TaskStatus
} from '@/api/project'
import TaskCard from '@/components/TaskCard.vue'
import TaskEditModal from '@/components/TaskEditModal.vue'
import ProjectEditModal from '@/components/ProjectEditModal.vue'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => route.params.id as string)

const loading = ref(true)
const loadError = ref('')
const project = ref<Project | null>(null)
const phases = ref<Phase[]>([])
const tasks = ref<Task[]>([])

// 编辑模态框
const taskModalShow = ref(false)
const editingTask = ref<Task | null>(null)
const projectModalShow = ref(false)

// ============ 计算属性 ============
const totalTasks = computed(() => tasks.value.length)
const passedTasks = computed(() => tasks.value.filter(t => t.status === 'passed').length)
const progress = computed(() => {
  if (totalTasks.value === 0) return 0
  return Math.round((passedTasks.value / totalTasks.value) * 100)
})

const statusStats = computed(() => {
  const stats = { pending: 0, submitted: 0, passed: 0, failed: 0 }
  tasks.value.forEach(t => {
    stats[t.status as TaskStatus]++
  })
  return stats
})

// 按阶段分组任务（未关联阶段的归入"未分组"）
const tasksByPhase = computed(() => {
  const groups: { phase: Phase | null; tasks: Task[] }[] = []
  // 按阶段排序
  const sortedPhases = [...phases.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  sortedPhases.forEach(phase => {
    const phaseTasks = tasks.value
      .filter(t => t.phaseId === phase.id)
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    groups.push({ phase, tasks: phaseTasks })
  })
  // 未分组任务
  const ungrouped = tasks.value
    .filter(t => !t.phaseId || !phases.value.some(p => p.id === t.phaseId))
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  if (ungrouped.length > 0) {
    groups.push({ phase: null, tasks: ungrouped })
  }
  return groups
})

// ============ 方法 ============
async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const [proj, phs, tks] = await Promise.all([
      getProject(projectId.value),
      listPhases(projectId.value),
      listTasks(projectId.value)
    ])
    project.value = proj
    phases.value = phs
    tasks.value = tks
  } catch (e: unknown) {
    loadError.value = (e as Error).message || '加载项目失败'
  } finally {
    loading.value = false
  }
}

function handleAddTask() {
  editingTask.value = null
  taskModalShow.value = true
}

function handleGeneratePlan() {
  router.push({ name: 'generate-plan', params: { id: projectId.value } })
}

function handleEditTask(task: Task) {
  editingTask.value = task
  taskModalShow.value = true
}

async function handleDeleteTask(task: Task) {
  try {
    await deleteTask(task.id)
    window.$message?.success('任务已删除')
    await loadData()
  } catch {
    // 错误已由拦截器处理
  }
}

function handleSubmitTask(task: Task) {
  router.push({ name: 'task-submit', params: { taskId: task.id } })
}

function handleEditProject() {
  projectModalShow.value = true
}

async function handleDeletePhase(phase: Phase) {
  try {
    await deletePhase(phase.id)
    window.$message?.success('阶段已删除')
    await loadData()
  } catch {
    // 错误已由拦截器处理
  }
}

function handleTaskSaved() {
  loadData()
}

function handleProjectSaved() {
  loadData()
}

function goBack() {
  router.push({ name: 'projects' })
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function phaseProgress(groupTasks: Task[]): number {
  if (groupTasks.length === 0) return 0
  const passed = groupTasks.filter(t => t.status === 'passed').length
  return Math.round((passed / groupTasks.length) * 100)
}

onMounted(loadData)
</script>

<template>
  <div class="project-detail-page">
    <!-- 顶部导航 -->
    <n-space justify="space-between" align="center" style="margin-bottom: 16px">
      <n-button quaternary @click="goBack">
        <template #icon><n-icon :component="ArrowBackOutline" /></template>
        返回项目列表
      </n-button>
      <n-space>
        <n-button v-if="project" @click="handleEditProject">
          <template #icon><n-icon :component="CreateOutline" /></template>
          编辑项目
        </n-button>
        <n-button v-if="project" type="info" @click="handleGeneratePlan">
          <template #icon><n-icon :component="SparklesOutline" /></template>
          AI 生成路线
        </n-button>
        <n-button type="primary" :disabled="!project" @click="handleAddTask">
          <template #icon><n-icon :component="AddOutline" /></template>
          添加任务
        </n-button>
      </n-space>
    </n-space>

    <!-- 加载中 -->
    <n-spin v-if="loading" size="large" style="display: flex; justify-content: center; padding: 80px 0" />

    <!-- 加载失败 -->
    <n-result v-else-if="loadError" status="error" title="加载失败" :description="loadError">
      <template #footer>
        <n-button @click="goBack">返回</n-button>
      </template>
    </n-result>

    <template v-else-if="project">
      <!-- 项目信息卡 + 进度 -->
      <n-card class="info-card" :bordered="false" style="margin-bottom: 16px; border-radius: 10px">
        <div class="cover-bar" :style="{ background: project.coverColor || '#18a058' }"></div>
        <div class="info-body">
          <n-space justify="space-between" align="start">
            <div style="flex: 1">
              <n-space align="center" size="small" style="margin-bottom: 8px">
                <n-text strong style="font-size: 22px">{{ project.name }}</n-text>
                <n-tag v-if="project.isAiGenerated" size="small" type="info" round>
                  <template #icon><n-icon :component="SparklesOutline" /></template>
                  AI 生成
                </n-tag>
                <n-tag size="small" :color="{ color: project.coverColor || '#18a058', textColor: '#fff' }" round>
                  {{ project.status || '进行中' }}
                </n-tag>
              </n-space>

              <n-text v-if="project.goal" depth="3" style="font-size: 14px; display: block; margin-bottom: 6px">
                🎯 {{ project.goal }}
              </n-text>

              <n-text v-if="project.description" depth="2" style="font-size: 14px; line-height: 1.6; display: block">
                {{ project.description }}
              </n-text>
            </div>

            <!-- 进度环 -->
            <div class="progress-block">
              <n-progress
                type="circle"
                :percentage="progress"
                :color="project.coverColor || '#18a058'"
                :stroke-width="6"
                style="width: 100px"
              >
                <n-text strong>{{ progress }}%</n-text>
              </n-progress>
              <n-text depth="3" style="font-size: 12px; display: block; text-align: center; margin-top: 4px">
                {{ passedTasks }} / {{ totalTasks }} 已完成
              </n-text>
            </div>
          </n-space>

          <!-- 统计 -->
          <n-grid :cols="4" :x-gap="16" style="margin-top: 20px">
            <n-gi>
              <n-statistic label="总任务" :value="totalTasks">
                <template #prefix><n-icon :component="LayersOutline" color="#909399" /></template>
              </n-statistic>
            </n-gi>
            <n-gi>
              <n-statistic label="待提交" :value="statusStats.pending">
                <template #prefix><n-icon :component="TimeOutline" color="#909399" /></template>
              </n-statistic>
            </n-gi>
            <n-gi>
              <n-statistic label="已通过" :value="statusStats.passed">
                <template #prefix><n-icon :component="CheckmarkDoneOutline" color="#18a058" /></template>
              </n-statistic>
            </n-gi>
            <n-gi>
              <n-statistic label="每日时长" :value="`${project.dailyHours || 0}h`">
                <template #prefix><n-icon :component="FlagOutline" color="#909399" /></template>
              </n-statistic>
            </n-gi>
          </n-grid>
        </div>
      </n-card>

      <!-- Timeline 时间轴 -->
      <n-card title="学习路线" :bordered="false" style="border-radius: 10px">
        <n-empty
          v-if="tasksByPhase.length === 0"
          description="还没有任务，点击右上角添加第一个任务吧"
          style="padding: 40px 0"
        >
          <template #extra>
            <n-button type="primary" @click="handleAddTask">
              <template #icon><n-icon :component="AddOutline" /></template>
              添加任务
            </n-button>
          </template>
        </n-empty>

        <n-timeline v-else size="large">
          <n-timeline-item
            v-for="(group, idx) in tasksByPhase"
            :key="group.phase?.id || `ungrouped-${idx}`"
            type="info"
            :time="group.phase ? `阶段 ${idx + 1}` : '未分组'"
          >
            <!-- 阶段标题 -->
            <template #header>
              <n-space align="center" justify="space-between" style="width: 100%">
                <n-space align="center" size="small">
                  <n-text strong style="font-size: 15px">
                    {{ group.phase ? group.phase.name : '未分组任务' }}
                  </n-text>
                  <n-tag size="tiny" round>{{ group.tasks.length }} 个任务</n-tag>
                  <n-tag v-if="group.phase && phaseProgress(group.tasks) === 100" size="tiny" type="success" round>
                    已完成
                  </n-tag>
                </n-space>
                <n-space v-if="group.phase" size="small" @click.stop>
                  <n-popconfirm @positive-click="handleDeletePhase(group.phase!)">
                    <template #trigger>
                      <n-button size="tiny" quaternary type="error">
                        <template #icon><n-icon :component="TrashOutline" /></template>
                        删除阶段
                      </n-button>
                    </template>
                    删除阶段会保留其下任务（变为未分组），确认删除？
                  </n-popconfirm>
                </n-space>
              </n-space>
            </template>

            <!-- 阶段目标 -->
            <n-text v-if="group.phase?.objective" depth="3" style="font-size: 13px; display: block; margin-bottom: 8px">
              📌 {{ group.phase.objective }}
            </n-text>

            <!-- 阶段进度条 -->
            <n-progress
              v-if="group.tasks.length > 0"
              type="line"
              :percentage="phaseProgress(group.tasks)"
              :height="4"
              style="margin-bottom: 12px"
            />
            <!-- 已在上方处理 -->

            <!-- 任务列表 -->
            <n-space vertical size="small">
              <TaskCard
                v-for="task in group.tasks"
                :key="task.id"
                :task="task"
                @submit="handleSubmitTask"
                @edit="handleEditTask"
                @delete="handleDeleteTask"
              />
            </n-space>
          </n-timeline-item>
        </n-timeline>
      </n-card>
    </template>

    <!-- 模态框 -->
    <TaskEditModal
      v-model:show="taskModalShow"
      :project-id="projectId"
      :phases="phases"
      :task="editingTask"
      @saved="handleTaskSaved"
    />
    <ProjectEditModal
      v-model:show="projectModalShow"
      :project="project"
      @saved="handleProjectSaved"
    />
  </div>
</template>

<style scoped lang="scss">
.project-detail-page {
  max-width: 1000px;
  margin: 0 auto;
}

.info-card {
  overflow: hidden;
  position: relative;

  :deep(.n-card__content) {
    padding: 0;
  }
}

.cover-bar {
  height: 4px;
  width: 100%;
}

.info-body {
  padding: 20px;
}

.progress-block {
  text-align: center;
  padding-left: 20px;
}
</style>
