import type { CapacitorConfig } from '@capacitor/cli'

/**
 * Capacitor 配置 · 把 Vue 3 站点打包为 Android 原生应用
 *
 * 关键约定：
 *  - webDir 必须与 vite build 的 outDir 一致（dist）
 *  - 生产环境 WebView 通过 https://localhost 加载本地 assets，
 *    因此 CI 构建时 vite 必须使用相对 base（--base=./）
 *  - appId 一旦发布到 Google Play 后不可修改，请谨慎设置
 */
const config: CapacitorConfig = {
  appId: 'com.learnspark.app',
  appName: 'LearnSpark',
  webDir: 'dist',
  android: {
    // 允许 https + http 混合（生产建议保持 false）
    allowMixedContent: false,
    // 捕获输入到原生输入法（解决中文输入法兼容）
    captureInput: true,
    // WebView 背景色，避免启动白屏
    backgroundColor: '#121212'
  },
  server: {
    // 留空 = 加载本地 assets；如需调试可在本地用局域网 IP 覆盖
    // url: 'http://192.168.1.100:5173',
    // cleartext: true,
    androidScheme: 'https'
  }
}

export default config
