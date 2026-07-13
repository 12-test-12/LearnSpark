/**
 * 提醒 API（离线版）— 接口签名不变
 * 离线版使用本地存储配置（Local Notifications 可后续集成）
 */
import { getDatabase, now } from '@/db/database'

export interface ReminderResponse {
  email: string
  reminderTime: string | null
  timezone: string
  enabled: boolean
  lastSentAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface ReminderRequest {
  email: string
  reminderTime: string
  timezone?: string
  enabled?: boolean
}

export async function getReminder(): Promise<ReminderResponse> {
  const db = await getDatabase()
  const result = await db.query(
    "SELECT * FROM reminder_settings WHERE user_id = 'local-user'"
  )
  const row = result.values?.[0]
  if (!row) {
    return {
      email: '',
      reminderTime: null,
      timezone: 'Asia/Shanghai',
      enabled: false,
      lastSentAt: null,
      createdAt: null,
      updatedAt: null,
    }
  }
  return {
    email: '',
    reminderTime: row.reminder_time,
    timezone: row.timezone,
    enabled: !!row.enabled,
    lastSentAt: row.last_sent_at,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  }
}

export async function saveReminder(data: ReminderRequest): Promise<ReminderResponse> {
  const db = await getDatabase()
  const existing = await db.query(
    "SELECT user_id FROM reminder_settings WHERE user_id = 'local-user'"
  )

  if (existing.values && existing.values.length > 0) {
    await db.run(
      `UPDATE reminder_settings SET reminder_time = ?, timezone = ?, enabled = ?, updated_at = ? WHERE user_id = 'local-user'`,
      [data.reminderTime, data.timezone ?? 'Asia/Shanghai', data.enabled === false ? 0 : 1, now()]
    )
  } else {
    await db.run(
      `INSERT INTO reminder_settings (user_id, reminder_time, timezone, enabled, created_at, updated_at) VALUES ('local-user', ?, ?, ?, ?, ?)`,
      [data.reminderTime, data.timezone ?? 'Asia/Shanghai', data.enabled === false ? 0 : 1, now(), now()]
    )
  }

  return getReminder()
}
