import { request } from './request'

/** 徽章展示项（含是否已解锁） */
export interface BadgeItem {
  id: string
  code: string
  name: string
  description: string | null
  iconUrl: string | null
  category: string | null
  ruleType: string | null
  ruleValue: number | null
  awarded: boolean
  awardedAt: string | null
}

/** 查询当前用户全部徽章（含已解锁/未解锁） */
export function getBadges(): Promise<BadgeItem[]> {
  return request.get<BadgeItem[]>('/user/badges')
}
