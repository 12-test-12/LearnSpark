import { request } from './request'

/** 提醒设置响应 */
export interface ReminderResponse {
  email: string
  reminderTime: string | null  // "HH:mm:ss" 格式
  timezone: string
  enabled: boolean
  lastSentAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

/** 提醒设置更新请求 */
export interface ReminderRequest {
  email: string
  reminderTime: string  // "HH:mm:ss" 格式
  timezone?: string
  enabled?: boolean
}

/** 获取当前用户提醒设置 */
export function getReminder(): Promise<ReminderResponse> {
  return request.get<ReminderResponse>('/user/reminder')
}

/** 更新当前用户提醒设置 */
export function saveReminder(data: ReminderRequest): Promise<ReminderResponse> {
  return request.put<ReminderResponse>('/user/reminder', data)
}
