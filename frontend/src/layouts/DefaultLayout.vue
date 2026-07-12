<script setup lang="ts">
import { h, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NLayout, NLayoutHeader, NLayoutSider, NLayoutContent, NMenu, NIcon, NButton, NSpace, NAvatar, NText, NDropdown, NTooltip } from 'naive-ui'
import type { MenuOption } from 'naive-ui'
import {
  GridOutline,
  BookOutline,
  LibraryOutline,
  TrophyOutline,
  SettingsOutline,
  LogOutOutline,
  PersonOutline,
  Sparkles,
  SunnyOutline,
  MoonOutline,
  DesktopOutline
} from '@vicons/ionicons5'
import { useUserStore } from '@/stores/user'
import { useThemeStore, type ThemeMode } from '@/stores/theme'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const themeStore = useThemeStore()

const collapsed = ref(false)

function renderIcon(icon: Component) {
  return () => h(NIcon, null, { default: () => h(icon) })
}

type Component = typeof GridOutline

const menuOptions: MenuOption[] = [
  { label: '仪表盘', key: 'dashboard', icon: renderIcon(GridOutline) },
  { label: '学习项目', key: 'projects', icon: renderIcon(BookOutline) },
  { label: '知识库', key: 'knowledge', icon: renderIcon(LibraryOutline) },
  { label: '成就', key: 'achievements', icon: renderIcon(TrophyOutline) },
  { label: '设置', key: 'settings', icon: renderIcon(SettingsOutline) }
]

const activeKey = computed(() => (route.name as string) || 'dashboard')

function handleMenuUpdate(key: string) {
  router.push({ name: key })
}

const userDropdownOptions = [
  { label: '个人设置', key: 'profile', icon: renderIcon(PersonOutline) },
  { label: '退出登录', key: 'logout', icon: renderIcon(LogOutOutline) }
]

function handleUserAction(key: string) {
  if (key === 'logout') {
    userStore.clearAuth()
    router.push({ name: 'login' })
  } else if (key === 'profile') {
    router.push({ name: 'settings' })
  }
}

// 主题切换图标：light→太阳 / dark→月亮 / auto→桌面
const themeIcon = computed(() => {
  const map: Record<ThemeMode, Component> = {
    light: SunnyOutline,
    dark: MoonOutline,
    auto: DesktopOutline
  }
  return map[themeStore.mode]
})

const themeLabel = computed(() => {
  const map: Record<ThemeMode, string> = {
    light: '浅色模式',
    dark: '深色模式',
    auto: '跟随系统'
  }
  return map[themeStore.mode]
})
</script>

<template>
  <n-layout has-sider position="absolute">
    <!-- 侧边栏 -->
    <n-layout-sider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="220"
      :collapsed="collapsed"
      show-trigger
      @collapse="collapsed = true"
      @expand="collapsed = false"
    >
      <div class="logo">
        <n-icon :size="24" color="#18a058"><Sparkles /></n-icon>
        <n-text v-if="!collapsed" strong>LearnSpark</n-text>
      </div>
      <n-menu
        :value="activeKey"
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :options="menuOptions"
        @update:value="handleMenuUpdate"
      />
    </n-layout-sider>

    <n-layout>
      <!-- 顶栏 -->
      <n-layout-header bordered class="header">
        <n-space justify="space-between" align="center" style="height: 100%; padding: 0 20px">
          <n-text strong>{{ route.meta.title || 'LearnSpark' }}</n-text>
          <n-space align="center" :size="8">
            <!-- 主题切换 -->
            <n-tooltip>
              <template #trigger>
                <n-button quaternary circle @click="themeStore.cycleMode()">
                  <template #icon>
                    <n-icon :component="themeIcon" />
                  </template>
                </n-button>
              </template>
              {{ themeLabel }}
            </n-tooltip>
            <!-- 用户菜单 -->
            <n-dropdown :options="userDropdownOptions" trigger="click" @select="handleUserAction">
              <n-button quaternary circle>
                <template #icon>
                  <n-avatar
                    round
                    size="small"
                    color="#18a058"
                  >
                    {{ userStore.userInfo?.nickname?.charAt(0) || 'U' }}
                  </n-avatar>
                </template>
              </n-button>
            </n-dropdown>
          </n-space>
        </n-space>
      </n-layout-header>

      <!-- 内容区 -->
      <n-layout-content class="content" content-style="padding: 20px">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<style scoped lang="scss">
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid var(--n-border-color, #efeff5);
}

.header {
  height: 56px;
  display: flex;
  align-items: center;
}

.content {
  height: calc(100vh - 56px);
  overflow: auto;
}
</style>
