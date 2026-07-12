import { createDiscreteApi } from 'naive-ui'

/**
 * Naive UI 离散 API：在组件外（如 axios 拦截器、定时任务）也能使用
 * message / dialog / notification / loadingBar。
 *
 * 组件内仍推荐使用 useMessage / useDialog 等 hooks，
 * 这里主要为非组件场景提供全局反馈能力。
 */
const { message, dialog, notification, loadingBar } = createDiscreteApi([
  'message',
  'dialog',
  'notification',
  'loadingBar'
])

// 挂到 window，供 request.ts 拦截器使用
declare global {
  interface Window {
    $message: typeof message
    $dialog: typeof dialog
    $notification: typeof notification
    $loadingBar: typeof loadingBar
  }
}

window.$message = message
window.$dialog = dialog
window.$notification = notification
window.$loadingBar = loadingBar

export { message, dialog, notification, loadingBar }
