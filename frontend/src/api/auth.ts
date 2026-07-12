import { request } from './request'
import type { UserInfo } from '@/stores/user'

export interface LoginParams {
  email: string
  password: string
}

export interface RegisterParams extends LoginParams {
  nickname?: string
}

export interface LoginResult {
  token: string
  userInfo: UserInfo
}

/**
 * 认证相关 API：注册、登录、获取当前用户。
 */
export function login(params: LoginParams): Promise<LoginResult> {
  return request.post<LoginResult>('/auth/login', params)
}

export function register(params: RegisterParams): Promise<LoginResult> {
  return request.post<LoginResult>('/auth/register', params)
}

export function getCurrentUser(): Promise<UserInfo> {
  return request.get<UserInfo>('/user/me')
}
