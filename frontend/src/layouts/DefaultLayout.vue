<script setup lang="ts">
import { h, ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NLayout, NLayoutHeader, NLayoutSider, NLayoutContent, NMenu, NIcon, NButton, NSpace, NAvatar, NText, NDropdown, NTooltip, NDrawer, NDrawerContent } from 'naive-ui'
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
  DesktopOutline,
  MenuOutline,
  CloseOutline
} from '@vicons/ionicons5'
import { useUserStore } from '@/stores/user'
import { useThemeStore, type ThemeMode } from '@/stores/theme'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const themeStore = useThemeStore()

/** 桌面端侧边栏折叠状态 */
const collapsed = ref(false)

/** 移动端检测 */
const isMobile = ref(false)
const mobileDrawerVisible = ref(false)

function checkScreen() {
  isMobile.value = window.innerWidth < 900
  // 桌面端默认展开侧边栏
  if (!isMobile.value) {
    collapsed.value = false
    mobileDrawerVisible.value = false
  }
}

onMounted(() => {
  checkScreen()
  window.addEventListener('resize', checkScreen)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkScreen)
})

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
  // 移动端选择后自动关闭抽屉
  if (isMobile.value) {
    mobileDrawerVisible.value = false
  }
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

// 主题切换图标
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

/** 桌面端侧边栏内容（抽出来复用） */
const sidebarContent = h('div', { class: 'sidebar-inner' }, [
  h('div', { class: 'logo' }, [
    h(NIcon, { size: 24, color: '#18a058' }, { default: () => h(Sparkles) }),
    !collapsed.value && h(NText, { strong: true }, { default: () => 'LearnSpark' })
  ]),
  h(NMenu, {
    value: activeKey.value,
    collapsed: collapsed.value,
    collapsedWidth: 64,
    collapsedIconSize: 22,
    options: menuOptions,
    'onUpdate:value': handleMenuUpdate
  })
])
</script>

<template>
  <n-layout has-sider position="absolute" class="layout-root">
    <!-- 桌面端侧边栏（≥ 900px 显示） -->
    <n-layout-sider
      v-if="!isMobile"
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="220"
      :collapsed="collapsed"
      show-trigger
      @collapse="collapsed = true"
      @expand="collapsed = false"
      class="desktop-sider"
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
        <n-space justify="space-between" align="center" style="height: 100%; padding: 0 16px">
          <n-space align="center" :size="12">
            <!-- 移动端：汉堡菜单按钮 -->
            <n-button
              v-if="isMobile"
              quaternary
              circle
              @click="mobileDrawerVisible = true"
            >
              <template #icon>
                <n-icon :component="MenuOutline" />
              </template>
            </n-button>
            <n-text strong class="page-title">{{ route.meta.title || 'LearnSpark' }}</n-text>
          </n-space>
          <n-space align="center" :size="4">
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
            <n-dropdown :options="userDropdownOptions" trigger="click" @select="handleUserAction">
              <n-button quaternary circle>
                <template #icon>
                  <n-avatar round size="small" color="#18a058">
                    {{ userStore.userInfo?.nickname?.charAt(0) || 'U' }}
                  </n-avatar>
                </template>
              </n-button>
            </n-dropdown>
          </n-space>
        </n-space>
      </n-layout-header>

      <!-- 内容区 -->
      <n-layout-content class="content">
        <router-view />
      </n-layout-content>
    </n-layout>

    <!-- 移动端：抽屉式侧边栏 -->
    <n-drawer
      v-if="isMobile"
      v-model:show="mobileDrawerVisible"
      :width="260"
      placement="left"
      :show-mask="true"
    >
      <n-drawer-content :show-icon="false" :native-scrollbar="false" class="mobile-drawer">
        <div class="logo logo-mobile">
          <n-icon :size="24" color="#18a058"><Sparkles /></n-icon>
          <n-text strong>LearnSpark</n-text>
        </div>
        <n-menu
          :value="activeKey"
          :options="menuOptions"
          :indent="18"
          @update:value="handleMenuUpdate"
        />
      </n-drawer-content>
    </n-drawer>
  </n-layout>
</template>

<style scoped lang="scss">
.layout-root {
  --header-height: 56px;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid var(--n-border-color, #efeff5);
  flex-shrink: 0;
}

.logo-mobile {
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  margin-bottom: 12px;
  justify-content: flex-start;
  padding-left: 8px;
}

.header {
  height: var(--header-height);
  display: flex;
  align-items: center;
}

.page-title {
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 60vw;
}

.content {
  height: calc(100vh - var(--header-height));
  overflow: auto;
  padding: 20px;
}

.mobile-drawer :deep(.n-drawer-body-content-wrapper) {
  padding: 16px 8px;
}

/* ========== 移动端适配 ========== */
@media (max-width: 720px) {
  .content {
    padding: 12px;
  }

  .page-title {
    font-size: 14px;
  }

  .header :deep(.n-space) {
    gap: 4px;
  }
}

/* 超小屏：进一步压缩 */
@media (max-width: 380px) {
  .content {
    padding: 8px;
  }
}
</style>
