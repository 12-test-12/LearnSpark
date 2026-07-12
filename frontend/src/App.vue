<script setup lang="ts">
import { computed, watch } from 'vue'
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

const themeStore = useThemeStore()

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
          <router-view />
        </n-dialog-provider>
      </n-message-provider>
    </n-loading-bar-provider>
  </n-config-provider>
</template>
