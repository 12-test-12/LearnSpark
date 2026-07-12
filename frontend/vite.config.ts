import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'
import { VitePWA } from 'vite-plugin-pwa'
import { fileURLToPath, URL } from 'node:url'

/**
 * ============================================================
 *  LearnSpark 前端构建配置
 *
 *  关键点（Capacitor 兼容）：
 *  1. base 必须为 './'，否则 Android WebView 通过 https://localhost 加载
 *     资源时绝对路径解析失败，导致 Capacitor Bridge 在启动时拿不到
 *     PackageManager 信息 → "packageinfo is null"
 *  2. Capacitor 模式下必须禁用 PWA Service Worker：
 *     - SW 会拦截 WebView 的 fetch，与 Capacitor 的本地资源加载冲突
 *     - navigateFallback: '/index.html' 在 WebView 中会触发循环重定向
 *     - 部分 WebView 实现下，SW 注册失败导致 Bridge 启动超时
 * ============================================================
 */

// 是否在 Capacitor 模式下构建（CI 中通过 CAPACITOR_BUILD=true 注入）
const isCapacitorBuild = process.env.CAPACITOR_BUILD === 'true'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // 加载 .env 文件
  const env = loadEnv(mode, process.cwd(), '')

  return {
    // ===== 关键修复 1: 相对路径 =====
    // Capacitor WebView 用 https://localhost 加载本地资源，
    // 绝对路径 '/' 会导致 404，触发 Bridge 启动失败
    base: './',

    plugins: [
      vue(),
      // 自动按需引入 vue / vue-router / pinia 的 API
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        dts: 'src/auto-imports.d.ts',
        eslintrc: { enabled: false }
      }),
      // 自动按需注册 Naive UI 组件
      Components({
        resolvers: [NaiveUiResolver()],
        dts: 'src/components.d.ts'
      }),
      // ===== 关键修复 2: Capacitor 模式禁用 PWA Service Worker =====
      // Service Worker 与 Capacitor Bridge 冲突是 "packageinfo is null" 的
      // 常见根因之一。Web 端仍可启用，Capacitor 端必须禁用。
      VitePWA({
        // 在 Capacitor 构建时完全禁用（包括 manifest、SW）
        disable: isCapacitorBuild,
        registerType: 'autoUpdate',
        includeAssets: ['favicon.svg'],
        manifest: {
          name: 'LearnSpark 灵犀学习',
          short_name: 'LearnSpark',
          description: 'AI 驱动的学习计划 + 个人知识库 + 勉励系统',
          theme_color: '#18a058',
          background_color: '#ffffff',
          display: 'standalone',
          start_url: '/',
          icons: [
            {
              src: 'pwa-192.png',
              sizes: '192x192',
              type: 'image/png'
            },
            {
              src: 'pwa-512.png',
              sizes: '512x512',
              type: 'image/png'
            },
            {
              src: 'pwa-512.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'maskable'
            }
          ]
        },
        workbox: {
          globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
          navigateFallback: '/index.html'
        }
      })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      },
      extensions: ['.ts', '.tsx', '.js', '.jsx', '.vue', '.json']
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      // 开发环境代理 /api 到后端
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    },
    build: {
      target: 'es2020',
      // Capacitor 模式下生成 source map（方便远程调试）
      // 生产 Web 部署关闭
      sourcemap: isCapacitorBuild,
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
            naive: ['naive-ui']
          }
        }
      }
    }
  }
})
