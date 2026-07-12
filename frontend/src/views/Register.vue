<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard,
  NForm,
  NFormItem,
  NInput,
  NButton,
  NSpace,
  NIcon,
  useMessage,
  darkTheme,
  type FormInst,
  type FormRules
} from 'naive-ui'
import { Sparkles } from '@vicons/ionicons5'
import { useUserStore } from '@/stores/user'
import { register } from '@/api/auth'

const router = useRouter()
const message = useMessage()
const userStore = useUserStore()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)

const model = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  nickname: ''
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度需在 6~50 位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value) => value === model.password,
      message: '两次输入的密码不一致',
      trigger: 'blur'
    }
  ]
}

async function handleRegister(e: MouseEvent) {
  e.preventDefault()
  formRef.value?.validate(async (errors) => {
    if (errors) return
    loading.value = true
    try {
      const result = await register({
        email: model.email,
        password: model.password,
        nickname: model.nickname || undefined
      })
      // 注册成功后自动登录
      userStore.setAuth(result.token, result.userInfo)
      message.success('注册成功，欢迎使用 LearnSpark！')
      router.push('/dashboard')
    } catch {
      // 错误提示由 axios 拦截器统一处理
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <!-- 固定编程编辑器深色背景，与代码编辑器视觉风格一致 -->
  <n-config-provider :theme="darkTheme">
    <div class="register-page">
      <div class="register-box">
      <!-- 品牌区 -->
      <div class="brand">
        <n-icon :size="40" color="#18a058"><Sparkles /></n-icon>
        <h1 class="title">LearnSpark</h1>
        <p class="subtitle">创建账号，开启你的 AI 学习之旅</p>
      </div>

      <!-- 注册卡片 -->
      <n-card :bordered="false" class="register-card">
        <n-form ref="formRef" :model="model" :rules="rules" size="large" label-placement="top">
          <n-form-item label="邮箱" path="email">
            <n-input v-model:value="model.email" placeholder="请输入邮箱" clearable />
          </n-form-item>
          <n-form-item label="昵称（可选）" path="nickname">
            <n-input v-model:value="model.nickname" placeholder="留空则使用邮箱" clearable />
          </n-form-item>
          <n-form-item label="密码" path="password">
            <n-input
              v-model:value="model.password"
              type="password"
              placeholder="至少 6 位"
              show-password-on="click"
            />
          </n-form-item>
          <n-form-item label="确认密码" path="confirmPassword">
            <n-input
              v-model:value="model.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              show-password-on="click"
              @keyup.enter="handleRegister"
            />
          </n-form-item>
          <n-button
            type="primary"
            block
            size="large"
            :loading="loading"
            @click="handleRegister"
          >
            注册
          </n-button>
          <n-space justify="center" style="margin-top: 16px">
            <span class="footer-hint">已有账号？</span>
            <n-button text type="primary" @click="router.push('/login')">返回登录</n-button>
          </n-space>
        </n-form>
      </n-card>

        <p class="copyright">LearnSpark · 灵犀学习</p>
      </div>
    </div>
  </n-config-provider>
</template>

<style scoped lang="scss">
/* 编程编辑器风格深色背景（VS Code Dark #1e1e1e） */
.register-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: #1e1e1e;
  color: #d4d4d4;
}

.register-box {
  width: 100%;
  max-width: 400px;
  text-align: center;
}

/* 品牌区 */
.brand {
  margin-bottom: 24px;
  color: #d4d4d4;
}

.title {
  margin: 8px 0 4px;
  font-size: 26px;
  font-weight: 700;
  color: #ffffff;
}

.subtitle {
  margin: 0;
  font-size: 13px;
  color: #9d9d9d;
}

/* 注册卡片 */
.register-card {
  text-align: left;
}

/* 底部提示文字 */
.footer-hint {
  font-size: 13px;
  color: #9d9d9d;
}

.copyright {
  margin-top: 20px;
  font-size: 12px;
  color: #6a6a6a;
}
</style>
