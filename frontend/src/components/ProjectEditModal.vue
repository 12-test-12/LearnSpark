<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import {
  NModal, NCard, NForm, NFormItem, NInput, NInputNumber, NColorPicker,
  NButton, NSpace
} from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import {
  createProject, updateProject, type Project, type ProjectRequest
} from '@/api/project'

const props = defineProps<{
  show: boolean
  /** 编辑模式时传入，新增为 null */
  project?: Project | null
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  saved: [project: Project]
}>()

const formRef = ref<FormInst | null>(null)
const saving = ref(false)
const isEdit = computed(() => !!props.project)

const form = reactive<ProjectRequest>({
  name: '',
  description: '',
  goal: '',
  dailyHours: 2,
  coverColor: '#18a058'
})

const rules: FormRules = {
  name: [{ required: true, message: '项目名称不能为空', trigger: 'blur' }]
}

watch(() => props.show, (visible) => {
  if (visible) {
    if (props.project) {
      form.name = props.project.name
      form.description = props.project.description || ''
      form.goal = props.project.goal || ''
      form.dailyHours = props.project.dailyHours ?? 2
      form.coverColor = props.project.coverColor || '#18a058'
    } else {
      form.name = ''
      form.description = ''
      form.goal = ''
      form.dailyHours = 2
      form.coverColor = '#18a058'
    }
  }
})

async function handleSave() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload: ProjectRequest = {
      name: form.name,
      description: form.description || undefined,
      goal: form.goal || undefined,
      dailyHours: form.dailyHours ?? 2,
      coverColor: form.coverColor
    }
    const saved = isEdit.value
      ? await updateProject(props.project!.id, payload)
      : await createProject(payload)
    window.$message?.success(isEdit.value ? '项目已更新' : '项目已创建')
    emit('saved', saved)
    emit('update:show', false)
  } finally {
    saving.value = false
  }
}

function handleClose() {
  emit('update:show', false)
}
</script>

<template>
  <n-modal
    :show="show"
    @update:show="emit('update:show', $event)"
    :mask-closable="false"
    style="width: 560px; max-width: 90vw"
  >
    <n-card
      :title="isEdit ? '编辑项目' : '新建项目'"
      :bordered="false"
      size="small"
      role="dialog"
      aria-modal="true"
    >
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
        <n-space vertical size="medium">
          <n-form-item label="项目名称" path="name">
            <n-input v-model:value="form.name" placeholder="如：Vue3 进阶学习" />
          </n-form-item>

          <n-form-item label="学习目标">
            <n-input
              v-model:value="form.goal"
              placeholder="一句话描述目标（可选）"
            />
          </n-form-item>

          <n-form-item label="项目描述">
            <n-input
              v-model:value="form.description"
              type="textarea"
              placeholder="详细描述项目背景与范围..."
              :autosize="{ minRows: 3, maxRows: 6 }"
            />
          </n-form-item>

          <n-space>
            <n-form-item label="每日学习时长（小时）">
              <n-input-number
                v-model:value="form.dailyHours"
                :min="1"
                :max="16"
                style="width: 160px"
              />
            </n-form-item>

            <n-form-item label="主题色">
              <n-color-picker
                v-model:value="form.coverColor"
                :modes="['hex']"
                :show-alpha="false"
                size="medium"
                style="width: 160px"
              />
            </n-form-item>
          </n-space>
        </n-space>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="handleClose">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">
            {{ isEdit ? '保存修改' : '创建项目' }}
          </n-button>
        </n-space>
      </template>
    </n-card>
  </n-modal>
</template>
