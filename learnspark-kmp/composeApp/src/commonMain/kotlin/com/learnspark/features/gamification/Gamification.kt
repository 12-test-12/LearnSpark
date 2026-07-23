package com.learnspark.features.gamification

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * 阶段 3.3：成就系统（按文档 §3.3 + §6.1 验证点）。
 *
 * 设计原则：
 * - 客户端本地权威（积分/徽章），离线也可累加
 * - 定期（联网时）同步到服务端
 * - 服务端仅记录历史 + 防作弊
 *
 * 核心实体：
 * - Achievement（徽章定义）：id, name, description, icon, rule
 * - UserAchievement（用户徽章）：userId, achievementId, unlockedAt
 * - PointLedger（积分流水）：userId, change, reason, createdAt
 * - StudyStreak（连续打卡）：userId, currentDays, longestDays, lastCheckInDate
 */

/**
 * 徽章定义。
 *
 * 内置 6 个核心徽章（按文档 §3.3 阶段）：
 * 1. first_task       初次提交
 * 2. streak_7         连续 7 天打卡
 * 3. streak_30        连续 30 天打卡
 * 4. knowledge_10     知识库收录 10 篇
 * 5. project_5        创建 5 个项目
 * 6. perfect_score    满分提交（AI 评分 100）
 */
@Serializable
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,                // emoji 或资源名
    val points: Int,                 // 解锁奖励积分
    val rule: AchievementRule,
)

@Serializable
sealed class AchievementRule {
    /** 首次完成某事 */
    data class FirstTime(val event: String) : AchievementRule()

    /** 连续打卡天数 */
    data class StreakDays(val days: Int) : AchievementRule()

    /** 知识库数量 */
    data class KnowledgeCount(val count: Int) : AchievementRule()

    /** 项目数量 */
    data class ProjectCount(val count: Int) : AchievementRule()

    /** 满分提交 */
    data object PerfectScore : AchievementRule()
}

/**
 * 用户已解锁的徽章。
 */
@Serializable
data class UserAchievement(
    val achievementId: String,
    val unlockedAt: String,         // ISO timestamp
    val notified: Boolean = false,   // 是否已通知用户
)

/**
 * 积分流水（append-only 账本）。
 */
@Serializable
data class PointEntry(
    val id: String,
    val change: Int,                 // 正数=获得，负数=消费
    val reason: String,              // 任务提交/徽章解锁/连续打卡/...
    val refId: String? = null,       // 关联资源 ID（任务/徽章/...）
    val createdAt: String,           // ISO timestamp
)

/**
 * 积分账户快照。
 */
@Serializable
data class PointAccount(
    val total: Int = 0,
    val level: Int = 1,
    val nextLevelPoints: Int = 100,  // 距离下一级所需积分
)

/**
 * 学习连续打卡记录。
 */
@Serializable
data class StudyStreak(
    val currentDays: Int = 0,
    val longestDays: Int = 0,
    val lastCheckInDate: String? = null,  // ISO date "YYYY-MM-DD"
)

/**
 * 阶段 3.3：成就系统门面 API（内部接口，UI 调用此层）。
 */
class GamificationService {
    private val unlocked: MutableMap<String, UserAchievement> = mutableMapOf()
    private val ledger: MutableList<PointEntry> = mutableListOf()
    private var streak: StudyStreak = StudyStreak()

    /**
     * 处理一次"事件"（任务提交/知识库添加/项目创建），返回本次获得的徽章 + 积分。
     */
    fun processEvent(
        event: String,
        context: GamificationContext,
    ): GamificationReward {
        val newPoints = mutableListOf<PointEntry>()
        val newBadges = mutableListOf<String>()

        // 基础积分
        when (event) {
            "task_submitted" -> {
                addPoints(10, "任务提交", event, newPoints)
            }
            "task_perfect_score" -> {
                addPoints(50, "满分提交", event, newPoints)
            }
            "knowledge_added" -> {
                addPoints(5, "知识库收录", event, newPoints)
            }
            "project_created" -> {
                addPoints(20, "创建项目", event, newPoints)
            }
            "check_in" -> {
                val today = LocalDate.now().toString()
                val last = streak.lastCheckInDate
                streak = if (last == null) {
                    StudyStreak(currentDays = 1, longestDays = 1, lastCheckInDate = today)
                } else {
                    val lastDate = LocalDate.parse(last)
                    val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(lastDate, LocalDate.now())
                    when {
                        daysBetween == 0L -> streak                                  // 今天已打卡
                        daysBetween == 1L -> streak.copy(
                            currentDays = streak.currentDays + 1,
                            longestDays = maxOf(streak.longestDays, streak.currentDays + 1),
                            lastCheckInDate = today,
                        )
                        else -> StudyStreak(currentDays = 1, longestDays = streak.longestDays, lastCheckInDate = today)
                    }
                }
                addPoints(5 * streak.currentDays, "连续打卡 ×${streak.currentDays}", event, newPoints)
            }
        }

        // 检查徽章解锁
        checkBadges(event, context, newBadges)

        return GamificationReward(
            newPoints = newPoints,
            newBadges = newBadges,
            currentStreak = streak,
        )
    }

    private fun addPoints(
        amount: Int,
        reason: String,
        refId: String,
        out: MutableList<PointEntry>,
    ) {
        val entry = PointEntry(
            id = java.util.UUID.randomUUID().toString(),
            change = amount,
            reason = reason,
            refId = refId,
            createdAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString(),
        )
        ledger.add(entry)
        out.add(entry)
    }

    private fun checkBadges(
        event: String,
        context: GamificationContext,
        newBadges: MutableList<String>,
    ) {
        // 首次提交
        if (event == "task_submitted" && context.totalSubmissions == 1 && !unlocked.containsKey("first_task")) {
            unlock("first_task", newBadges)
        }
        // 连续 7 / 30 天
        if (streak.currentDays >= 7 && !unlocked.containsKey("streak_7")) {
            unlock("streak_7", newBadges)
        }
        if (streak.currentDays >= 30 && !unlocked.containsKey("streak_30")) {
            unlock("streak_30", newBadges)
        }
        // 知识库 10
        if (context.knowledgeCount >= 10 && !unlocked.containsKey("knowledge_10")) {
            unlock("knowledge_10", newBadges)
        }
        // 项目 5
        if (context.projectCount >= 5 && !unlocked.containsKey("project_5")) {
            unlock("project_5", newBadges)
        }
        // 满分
        if (event == "task_perfect_score" && !unlocked.containsKey("perfect_score")) {
            unlock("perfect_score", newBadges)
        }
    }

    private fun unlock(achievementId: String, out: MutableList<String>) {
        unlocked[achievementId] = UserAchievement(
            achievementId = achievementId,
            unlockedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString(),
        )
        // 徽章奖励积分
        val ach = BUILT_IN_ACHIEVEMENTS.find { it.id == achievementId }
        if (ach != null) {
            val entry = PointEntry(
                id = java.util.UUID.randomUUID().toString(),
                change = ach.points,
                reason = "解锁徽章：${ach.name}",
                refId = achievementId,
                createdAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString(),
            )
            ledger.add(entry)
        }
        out.add(achievementId)
    }

    fun account(): PointAccount {
        val total = ledger.sumOf { it.change }
        val level = total / 100 + 1
        val nextLevelPoints = level * 100 - total
        return PointAccount(total = total, level = level, nextLevelPoints = nextLevelPoints)
    }

    fun unlockedBadges(): List<UserAchievement> = unlocked.values.toList()

    fun currentStreak(): StudyStreak = streak
}

data class GamificationContext(
    val totalSubmissions: Int = 0,
    val knowledgeCount: Int = 0,
    val projectCount: Int = 0,
)

data class GamificationReward(
    val newPoints: List<PointEntry>,
    val newBadges: List<String>,
    val currentStreak: StudyStreak,
)

/**
 * 内置徽章定义。
 */
val BUILT_IN_ACHIEVEMENTS: List<Achievement> = listOf(
    Achievement(
        id = "first_task",
        name = "初次启航",
        description = "提交第一份任务",
        icon = "🚀",
        points = 20,
        rule = AchievementRule.FirstTime("task_submitted"),
    ),
    Achievement(
        id = "streak_7",
        name = "坚持一周",
        description = "连续 7 天打卡",
        icon = "🔥",
        points = 50,
        rule = AchievementRule.StreakDays(7),
    ),
    Achievement(
        id = "streak_30",
        name = "月度精英",
        description = "连续 30 天打卡",
        icon = "🏆",
        points = 200,
        rule = AchievementRule.StreakDays(30),
    ),
    Achievement(
        id = "knowledge_10",
        name = "知识收藏家",
        description = "知识库收录 10 篇",
        icon = "📚",
        points = 30,
        rule = AchievementRule.KnowledgeCount(10),
    ),
    Achievement(
        id = "project_5",
        name = "多面手",
        description = "创建 5 个项目",
        icon = "📁",
        points = 50,
        rule = AchievementRule.ProjectCount(5),
    ),
    Achievement(
        id = "perfect_score",
        name = "完美主义",
        description = "获得一次 AI 满分评分",
        icon = "⭐",
        points = 100,
        rule = AchievementRule.PerfectScore,
    ),
)
