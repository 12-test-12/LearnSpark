import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'GridOutline' }
      },
      {
        path: 'projects',
        name: 'projects',
        component: () => import('@/views/Projects.vue'),
        meta: { title: '学习项目', icon: 'BookOutline' }
      },
      {
        path: 'projects/:id',
        name: 'project-detail',
        component: () => import('@/views/ProjectDetail.vue'),
        meta: { title: '项目详情' }
      },
      {
        path: 'projects/:id/generate-plan',
        name: 'generate-plan',
        component: () => import('@/views/GeneratePlan.vue'),
        meta: { title: 'AI 生成路线' }
      },
      {
        path: 'tasks/:taskId/submit',
        name: 'task-submit',
        component: () => import('@/views/TaskSubmit.vue'),
        meta: { title: '提交任务' }
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('@/views/KnowledgeBase.vue'),
        meta: { title: '知识库', icon: 'LibraryOutline' }
      },
      {
        path: 'knowledge/:id',
        name: 'knowledge-detail',
        component: () => import('@/views/KnowledgeDetail.vue'),
        meta: { title: '笔记详情' }
      },
      {
        path: 'achievements',
        name: 'achievements',
        component: () => import('@/views/Achievements.vue'),
        meta: { title: '成就', icon: 'TrophyOutline' }
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('@/views/Settings.vue'),
        meta: { title: '设置', icon: 'SettingsOutline' }
      }
    ],
    meta: { requiresAuth: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在', requiresAuth: false }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

// 全局前置守卫：登录态校验
router.beforeEach((to, _from, next) => {
  const appTitle = import.meta.env.VITE_APP_TITLE || 'LearnSpark'
  document.title = to.meta.title ? `${to.meta.title} · ${appTitle}` : appTitle

  const userStore = useUserStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !userStore.isLoggedIn()) {
    next({ name: 'login', query: { redirect: to.fullPath } })
  } else if ((to.name === 'login' || to.name === 'register') && userStore.isLoggedIn()) {
    next({ name: 'dashboard' })
  } else {
    next()
  }
})

export default router
