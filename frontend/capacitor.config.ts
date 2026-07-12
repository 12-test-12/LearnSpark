import type { CapacitorConfig } from '@capacitor/cli'

/**
 * Capacitor 配置 · 把 Vue 3 站点打包为 Android 原生应用
 *
 * 关键约定：
 *  - webDir 必须与 vite build 的 outDir 一致（dist）
 *  - 生产环境 WebView 通过 https://localhost 加载本地 assets
 *  - appId 一旦发布到 Google Play 后不可修改，请谨慎设置
 *
 *  修复 "packageinfo is null" 关键点：
 *  - webContentsDebuggingEnabled 在 release 包必须为 false
 *  - 关闭后 PackageManager 不会再被调试器干扰
 */
const isDebug = process.env.NODE_ENV === 'development' || process.env.CAPACITOR_DEBUG === 'true'

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
    backgroundColor: '#121212',
    // Release 包必须关闭 WebView 调试（开启会被某些 PackageManager 检查拦截）
    webContentsDebuggingEnabled: isDebug
  },
  server: {
    // 留空 = 加载本地 assets；如需调试可在本地用局域网 IP 覆盖
    // url: 'http://192.168.1.100:5173',
    // cleartext: true,
    androidScheme: 'https',
    // 禁用错误页（避免 WebView 加载失败时显示影响 PackageManager 的页面）
    errorPath: undefined
  }
}

export default config
