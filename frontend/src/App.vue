<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import {
  NConfigProvider,
  NMessageProvider,
  NDialogProvider,
  NLoadingBarProvider,
  zhCN,
  dateZhCN,
  darkTheme,
  type GlobalThemeOverrides
} from 'naive-ui'
import { useThemeStore } from '@/stores/theme'
import { getDatabase } from '@/db/database'

const themeStore = useThemeStore()

// 数据库初始化状态（离线模式启动时需要初始化 SQLite）
const dbReady = ref(false)
const dbError = ref('')

onMounted(async () => {
  try {
    await getDatabase()
    dbReady.value = true
    console.log('[App] SQLite 数据库初始化完成')
  } catch (e) {
    dbError.value = (e as Error).message
    console.error('[App] 数据库初始化失败:', e)
  }
})

// 主题色定制（呼应 LearnSpark 品牌，深浅模式共用）
const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#18a058',
    primaryColorHover: '#36ad6a',
    primaryColorPressed: '#0c7a43',
    borderRadius: '6px'
  }
}

// 深色模式时同步覆盖 body 背景，避免侧边留白
const theme = computed(() => themeStore.isDark ? darkTheme : null)

// body 背景同步放在 App.vue（SSOT），确保登录/注册等独立路由也能跟随主题
watch(() => themeStore.isDark, (dark) => {
  document.body.style.backgroundColor = dark ? '#18181c' : '#fff'
}, { immediate: true })
</script>

<template>
  <n-config-provider
    :locale="zhCN"
    :date-locale="dateZhCN"
    :theme="theme"
    :theme-overrides="themeOverrides"
  >
    <n-loading-bar-provider>
      <n-message-provider>
        <n-dialog-provider>
          <!-- 数据库初始化中 -->
          <div v-if="!dbReady && !dbError" style="display:flex;align-items:center;justify-content:center;height:100vh;flex-direction:column;gap:16px;">
            <n-spin size="large" />
            <p style="color:#666;">正在初始化本地数据库...</p>
          </div>
          <!-- 数据库初始化失败 -->
          <div v-else-if="dbError" style="display:flex;align-items:center;justify-content:center;height:100vh;flex-direction:column;gap:16px;padding:24px;">
            <n-result status="error" title="数据库初始化失败" :description="dbError" />
          </div>
          <!-- 正常显示 -->
          <router-view v-else />
        </n-dialog-provider>
      </n-message-provider>
    </n-loading-bar-provider>
  </n-config-provider>
</template>
