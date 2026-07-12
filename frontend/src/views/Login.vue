<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NCard,
  NForm,
  NFormItem,
  NInput,
  NButton,
  NIcon,
  useMessage,
  darkTheme,
  type FormInst,
  type FormRules
} from 'naive-ui'
import { Sparkles } from '@vicons/ionicons5'
import { useUserStore } from '@/stores/user'
import { login } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const userStore = useUserStore()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)

const model = reactive({
  email: '',
  password: ''
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  formRef.value?.validate(async (errors) => {
    if (errors) return
    loading.value = true
    try {
      const result = await login({ email: model.email, password: model.password })
      userStore.setAuth(result.token, result.userInfo)
      message.success('登录成功')
      const redirect = (route.query.redirect as string) || '/dashboard'
      router.push(redirect)
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
    <div class="login-page">
      <div class="login-box">
      <!-- 品牌区 -->
      <div class="brand">
        <n-icon :size="40" color="#18a058"><Sparkles /></n-icon>
        <h1 class="title">LearnSpark</h1>
        <p class="subtitle">灵犀学习 · AI 驱动的学习计划 + 知识库</p>
      </div>

      <!-- 登录卡片 -->
      <n-card :bordered="false" class="login-card">
        <n-form ref="formRef" :model="model" :rules="rules" size="large" label-placement="top">
          <n-form-item label="邮箱" path="email">
            <n-input
              v-model:value="model.email"
              placeholder="请输入邮箱"
              clearable
              @keyup.enter="handleLogin"
            />
          </n-form-item>
          <n-form-item label="密码" path="password">
            <n-input
              v-model:value="model.password"
              type="password"
              placeholder="请输入密码"
              show-password-on="click"
              @keyup.enter="handleLogin"
            />
          </n-form-item>
          <n-button
            type="primary"
            block
            size="large"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </n-button>
          <div class="form-footer">
            <n-button text type="primary" @click="router.push('/register')">注册账号</n-button>
            <span class="footer-divider">|</span>
            <n-button text>忘记密码</n-button>
          </div>
        </n-form>
      </n-card>

        <p class="copyright">LearnSpark · 灵犀学习</p>
      </div>
    </div>
  </n-config-provider>
</template>

<style scoped lang="scss">
/* 编程编辑器风格深色背景（VS Code Dark #1e1e1e） */
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: #1e1e1e;
  color: #d4d4d4;
}

.login-box {
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

/* 登录卡片 */
.login-card {
  text-align: left;
}

/* 底部链接：竖线分隔 */
.form-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;

  .footer-divider {
    color: #6a6a6a;
    font-size: 12px;
  }
}

.copyright {
  margin-top: 20px;
  font-size: 12px;
  color: #6a6a6a;
}
</style>
