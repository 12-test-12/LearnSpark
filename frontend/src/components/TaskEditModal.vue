<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import {
  NModal, NCard, NForm, NFormItem, NInput, NInputNumber, NDatePicker,
  NSelect, NButton, NSpace
} from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import { createTask, updateTask, type Task, type TaskRequest, type Phase } from '@/api/project'

const props = defineProps<{
  show: boolean
  projectId: string
  phases: Phase[]
  /** 编辑模式时传入的任务，新增模式为 null */
  task?: Task | null
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  saved: [task: Task]
}>()

const formRef = ref<FormInst | null>(null)
const saving = ref(false)

const isEdit = computed(() => !!props.task)

const form = reactive<TaskRequest>({
  projectId: props.projectId,
  phaseId: null,
  dayNumber: null,
  title: '',
  description: '',
  verificationCriteria: '',
  dueDate: null,
  sortOrder: 0
})

const dueDateTs = ref<number | null>(null)

const rules: FormRules = {
  description: [{ required: true, message: '任务描述不能为空', trigger: 'blur' }]
}

const phaseOptions = computed(() => [
  { label: '未关联阶段', value: '' },
  ...props.phases.map(p => ({ label: p.name, value: p.id }))
])

// 监听 show/task 变化重置表单
watch(() => props.show, (visible) => {
  if (visible) {
    form.projectId = props.projectId
    if (props.task) {
      // 编辑模式：填充已有数据
      form.phaseId = props.task.phaseId
      form.dayNumber = props.task.dayNumber
      form.title = props.task.title || ''
      form.description = props.task.description
      form.verificationCriteria = props.task.verificationCriteria || ''
      form.dueDate = props.task.dueDate
      form.sortOrder = props.task.sortOrder
      dueDateTs.value = props.task.dueDate ? new Date(props.task.dueDate).getTime() : null
    } else {
      // 新增模式：重置
      form.phaseId = null
      form.dayNumber = null
      form.title = ''
      form.description = ''
      form.verificationCriteria = ''
      form.dueDate = null
      form.sortOrder = (props.phases.length + 1) * 10
      dueDateTs.value = null
    }
  }
})

function handleDueDateChange(ts: number | null) {
  dueDateTs.value = ts
  form.dueDate = ts ? new Date(ts).toISOString().slice(0, 10) : null
}

function handlePhaseChange(value: string) {
  form.phaseId = value || null
}

async function handleSave() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload: TaskRequest = {
      projectId: form.projectId,
      phaseId: form.phaseId || undefined,
      dayNumber: form.dayNumber ?? undefined,
      title: form.title || undefined,
      description: form.description,
      verificationCriteria: form.verificationCriteria || undefined,
      dueDate: form.dueDate || null,
      sortOrder: form.sortOrder ?? 0
    }
    const saved = isEdit.value
      ? await updateTask(props.task!.id, payload)
      : await createTask(payload)
    window.$message?.success(isEdit.value ? '任务已更新' : '任务已创建')
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
    style="width: 600px; max-width: 90vw"
  >
    <n-card
      :title="isEdit ? '编辑任务' : '添加任务'"
      :bordered="false"
      size="small"
      role="dialog"
      aria-modal="true"
    >
      <n-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-placement="top"
      >
        <n-space vertical size="medium">
          <n-form-item label="任务标题" path="title">
            <n-input
              v-model:value="form.title"
              placeholder="简短标题（可选）"
            />
          </n-form-item>

          <n-form-item label="任务描述" path="description">
            <n-input
              v-model:value="form.description"
              type="textarea"
              placeholder="详细描述任务内容..."
              :autosize="{ minRows: 3, maxRows: 8 }"
            />
          </n-form-item>

          <n-form-item label="验证标准">
            <n-input
              v-model:value="form.verificationCriteria"
              type="textarea"
              placeholder="如何判断任务完成？（可选）"
              :autosize="{ minRows: 2, maxRows: 4 }"
            />
          </n-form-item>

          <n-space>
            <n-form-item label="所属阶段" style="width: 240px">
              <n-select
                :value="form.phaseId || ''"
                :options="phaseOptions"
                @update:value="handlePhaseChange"
              />
            </n-form-item>

            <n-form-item label="第几天" style="width: 120px">
              <n-input-number
                v-model:value="form.dayNumber"
                :min="1"
                :max="365"
                placeholder="Day N"
                style="width: 100%"
              />
            </n-form-item>

            <n-form-item label="截止日期" style="width: 200px">
              <n-date-picker
                :value="dueDateTs"
                type="date"
                clearable
                @update:value="handleDueDateChange"
              />
            </n-form-item>
          </n-space>
        </n-space>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="handleClose">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">
            {{ isEdit ? '保存修改' : '创建任务' }}
          </n-button>
        </n-space>
      </template>
    </n-card>
  </n-modal>
</template>
