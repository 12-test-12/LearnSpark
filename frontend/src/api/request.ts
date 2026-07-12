import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { useUserStore } from '@/stores/user'
import router from '@/router'

/**
 * 统一响应结构（与后端 ApiResult 对齐）
 */
interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000
  // 不预设 Content-Type：axios 会根据 data 类型自动设置
  // 普通对象 → application/json，FormData → multipart/form-data + boundary
})

// 请求拦截器：携带 token
service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一拆包 + 错误处理
service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const res = response.data
    // 后端业务码 0 表示成功
    if (res.code === 0) {
      return res.data as never
    }
    // 业务错误
    handleBusinessError(res.code, res.message)
    return Promise.reject(new Error(res.message || 'Error'))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      handleUnauthorized()
    } else if (status === 403) {
      window.$message?.error('无权访问')
    } else {
      const msg = error.response?.data?.message || error.message || '请求失败'
      window.$message?.error(msg)
    }
    return Promise.reject(error)
  }
)

function handleBusinessError(code: number, message: string) {
  // 40100 表示未登录或 token 过期
  if (code === 40100 || code === 10004) {
    handleUnauthorized()
  } else {
    window.$message?.error(message)
  }
}

function handleUnauthorized() {
  const userStore = useUserStore()
  userStore.clearAuth()
  window.$message?.warning('登录已过期，请重新登录')
  const redirect = router.currentRoute.value.fullPath
  if (router.currentRoute.value.name !== 'login') {
    router.push({ name: 'login', query: { redirect } })
  }
}

/** 封装请求方法，返回值直接是 data 部分 */
export const request = {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config) as unknown as Promise<T>
  },
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as unknown as Promise<T>
  },
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as unknown as Promise<T>
  },
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config) as unknown as Promise<T>
  },
  /** 原始 axios 实例，用于上传文件等特殊场景 */
  raw: service
}

export default service
export type { ApiResult }
