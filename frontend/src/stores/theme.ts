import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/** 主题模式：light / dark / auto（跟随系统） */
export type ThemeMode = 'light' | 'dark' | 'auto'

export const useThemeStore = defineStore(
  'theme',
  () => {
    const mode = ref<ThemeMode>('auto')

    /** 系统是否偏好深色 */
    const systemPrefersDark = ref(
      window.matchMedia('(prefers-color-scheme: dark)').matches
    )

    // 监听系统主题变化（auto 模式下实时切换）
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
      systemPrefersDark.value = e.matches
    })

    /** 实际是否为深色模式 */
    const isDark = computed(() => {
      if (mode.value === 'auto') {
        return systemPrefersDark.value
      }
      return mode.value === 'dark'
    })

    /** 切换主题模式（light → dark → auto 循环） */
    function cycleMode() {
      const order: ThemeMode[] = ['light', 'dark', 'auto']
      const idx = order.indexOf(mode.value)
      mode.value = order[(idx + 1) % order.length]
    }

    /** 直接设置主题模式 */
    function setMode(m: ThemeMode) {
      mode.value = m
    }

    return {
      mode,
      isDark,
      cycleMode,
      setMode
    }
  },
  {
    // 持久化到 localStorage，刷新后保持
    persist: {
      key: 'learnspark-theme',
      storage: localStorage,
      paths: ['mode']
    }
  }
)
