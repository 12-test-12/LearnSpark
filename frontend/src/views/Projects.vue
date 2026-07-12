<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NButton, NSpace, NText, NTag, NEmpty, NSpin, NIcon,
  NGrid, NGi, NPopconfirm
} from 'naive-ui'
import {
  AddOutline, CreateOutline, TrashOutline, ArrowForwardOutline,
  TimeOutline, SparklesOutline, BookOutline
} from '@vicons/ionicons5'
import { listProjects, deleteProject, type Project } from '@/api/project'
import ProjectEditModal from '@/components/ProjectEditModal.vue'

const router = useRouter()

const loading = ref(true)
const projects = ref<Project[]>([])
const editModalShow = ref(false)
const editingProject = ref<Project | null>(null)

async function loadProjects() {
  loading.value = true
  try {
    projects.value = await listProjects()
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  editingProject.value = null
  editModalShow.value = true
}

function handleEdit(project: Project) {
  editingProject.value = project
  editModalShow.value = true
}

async function handleDelete(project: Project) {
  try {
    await deleteProject(project.id)
    window.$message?.success('项目已删除')
    await loadProjects()
  } catch {
    // 错误已由拦截器处理
  }
}

function handleOpen(project: Project) {
  router.push({ name: 'project-detail', params: { id: project.id } })
}

function handleGeneratePlan(project: Project) {
  router.push({ name: 'generate-plan', params: { id: project.id } })
}

function handleProjectSaved() {
  loadProjects()
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

onMounted(loadProjects)
</script>

<template>
  <div class="projects-page">
    <!-- 顶部操作栏 -->
    <n-space justify="space-between" align="center" style="margin-bottom: 20px">
      <div>
        <n-space align="center" size="small">
          <n-icon :component="BookOutline" :size="22" color="#18a058" />
          <n-text strong style="font-size: 18px">学习项目</n-text>
          <n-tag v-if="!loading" size="small" round>{{ projects.length }} 个</n-tag>
        </n-space>
      </div>
      <n-button type="primary" @click="handleCreate">
        <template #icon><n-icon :component="AddOutline" /></template>
        新建项目
      </n-button>
    </n-space>

    <!-- 加载中 -->
    <n-spin v-if="loading" size="large" style="display: flex; justify-content: center; padding: 80px 0" />

    <!-- 空状态 -->
    <n-empty
      v-else-if="projects.length === 0"
      description="还没有学习项目，点击右上角新建第一个项目吧"
      style="padding: 80px 0"
    >
      <template #extra>
        <n-button type="primary" @click="handleCreate">
          <template #icon><n-icon :component="AddOutline" /></template>
          新建项目
        </n-button>
      </template>
    </n-empty>

    <!-- 项目卡片网格 -->
    <n-grid v-else :cols="3" :x-gap="16" :y-gap="16" responsive="screen" item-responsive>
      <n-gi v-for="project in projects" :key="project.id" span="3 m:2 l:1">
        <n-card
          class="project-card"
          :bordered="true"
          hoverable
          @click="handleOpen(project)"
        >
          <!-- 顶部色条 -->
          <div class="cover-bar" :style="{ background: project.coverColor || '#18a058' }"></div>

          <div class="card-body">
            <!-- 标题区 -->
            <n-space justify="space-between" align="start" style="margin-bottom: 8px">
              <n-text strong style="font-size: 16px">{{ project.name }}</n-text>
              <n-tag v-if="project.isAiGenerated" size="tiny" type="info" round>
                <template #icon><n-icon :component="SparklesOutline" /></template>
                AI
              </n-tag>
            </n-space>

            <!-- 目标 -->
            <n-text v-if="project.goal" depth="3" class="goal-text">{{ project.goal }}</n-text>

            <!-- 描述 -->
            <n-text v-if="project.description" depth="2" class="desc-text">
              {{ project.description }}
            </n-text>

            <!-- 底部信息 -->
            <div class="card-footer">
              <n-space align="center" size="small">
                <n-tag size="small" :color="{ color: project.coverColor || '#18a058', textColor: '#fff' }" round>
                  {{ project.status || '进行中' }}
                </n-tag>
                <n-text v-if="project.dailyHours" depth="3" style="font-size: 12px">
                  <n-icon :component="TimeOutline" size="12" style="vertical-align: -2px" />
                  {{ project.dailyHours }}h/日
                </n-text>
              </n-space>

              <!-- 操作按钮（阻止冒泡避免触发卡片点击） -->
              <n-space size="small" @click.stop>
                <n-button size="small" quaternary type="info" @click="handleGeneratePlan(project)">
                  <template #icon><n-icon :component="SparklesOutline" /></template>
                </n-button>
                <n-button size="small" quaternary @click="handleEdit(project)">
                  <template #icon><n-icon :component="CreateOutline" /></template>
                </n-button>
                <n-popconfirm @positive-click="handleDelete(project)">
                  <template #trigger>
                    <n-button size="small" quaternary type="error">
                      <template #icon><n-icon :component="TrashOutline" /></template>
                    </n-button>
                  </template>
                  确认删除项目「{{ project.name }}」？
                </n-popconfirm>
              </n-space>
            </div>

            <!-- 创建时间 -->
            <n-text depth="3" style="font-size: 11px; display: block; margin-top: 8px">
              创建于 {{ formatDate(project.createdAt) }}
            </n-text>
          </div>

          <!-- 打开提示 -->
          <div class="open-hint">
            <n-text depth="3" style="font-size: 12px">查看详情</n-text>
            <n-icon :component="ArrowForwardOutline" size="14" />
          </div>
        </n-card>
      </n-gi>
    </n-grid>

    <!-- 项目编辑模态框 -->
    <ProjectEditModal
      v-model:show="editModalShow"
      :project="editingProject"
      @saved="handleProjectSaved"
    />
  </div>
</template>

<style scoped lang="scss">
.projects-page {
  max-width: 1200px;
  margin: 0 auto;
}

.project-card {
  cursor: pointer;
  border-radius: 10px;
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);

    .open-hint {
      opacity: 1;
    }
  }

  :deep(.n-card__content) {
    padding: 0;
  }
}

.cover-bar {
  height: 4px;
  width: 100%;
}

.card-body {
  padding: 16px;
}

.goal-text {
  font-size: 13px;
  display: block;
  margin-bottom: 6px;
  font-style: italic;
}

.desc-text {
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.open-hint {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}
</style>
