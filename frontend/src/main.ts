import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import App from './App.vue'
import router from './router'
// 初始化 Naive UI 离散 API（必须在 app.mount 前，确保 window.$message 可用）
import '@/utils/feedback'
import 'vfonts/Lato.css'
import 'vfonts/FiraCode.css'
import '@/assets/styles/main.scss'

const app = createApp(App)

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app.use(pinia)
app.use(router)

app.mount('#app')
