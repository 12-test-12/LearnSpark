/**
 * 徽章 API（离线版）— 接口签名不变，底层改为 SQLite
 */
import { gamificationRepo } from '@/db/repositories'

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

export async function getBadges(): Promise<BadgeItem[]> {
  const allBadges = await gamificationRepo.listBadges()
  const userBadges = await gamificationRepo.listUserBadges()
  const awardedMap = new Map(userBadges.map(b => [b.id, b.awarded_at]))

  return allBadges.map(b => ({
    id: b.id,
    code: b.code,
    name: b.name,
    description: b.description,
    iconUrl: b.icon_url,
    category: b.category,
    ruleType: b.rule_type,
    ruleValue: b.rule_value,
    awarded: awardedMap.has(b.id),
    awardedAt: awardedMap.get(b.id) ?? null,
  }))
}
