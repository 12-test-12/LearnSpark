/**
 * 认证 API（离线版）— 离线模式无需认证
 *
 * 所有方法返回固定的本地用户，不做任何验证
 * 保持接口签名不变，组件代码无需修改
 */
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

const LOCAL_TOKEN = 'offline-mode'
const LOCAL_USER: UserInfo = {
  id: 'local-user',
  email: 'local@learnspark.app',
  nickname: '我',
  avatarUrl: '',
}

/** 离线模式：直接返回本地用户 */
export async function login(_params: LoginParams): Promise<LoginResult> {
  return { token: LOCAL_TOKEN, userInfo: LOCAL_USER }
}

/** 离线模式：直接返回本地用户 */
export async function register(params: RegisterParams): Promise<LoginResult> {
  if (params.nickname) {
    LOCAL_USER.nickname = params.nickname
  }
  return { token: LOCAL_TOKEN, userInfo: LOCAL_USER }
}

/** 离线模式：返回本地用户 */
export async function getCurrentUser(): Promise<UserInfo> {
  return LOCAL_USER
}
