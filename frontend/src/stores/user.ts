import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface UserInfo {
  id: string
  email: string
  nickname: string
  avatarUrl?: string
  timezone?: string
}

export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>('')
    const userInfo = ref<UserInfo | null>(null)

    function setAuth(t: string, info: UserInfo) {
      token.value = t
      userInfo.value = info
    }

    function setToken(t: string) {
      token.value = t
    }

    function setUserInfo(info: UserInfo) {
      userInfo.value = info
    }

    function clearAuth() {
      token.value = ''
      userInfo.value = null
    }

    function isLoggedIn(): boolean {
      return !!token.value
    }

    return {
      token,
      userInfo,
      setAuth,
      setToken,
      setUserInfo,
      clearAuth,
      isLoggedIn
    }
  },
  {
    // 持久化到 localStorage（token 与用户信息刷新后保持）
    persist: {
      key: 'learnspark-user',
      storage: localStorage,
      paths: ['token', 'userInfo']
    }
  }
)
