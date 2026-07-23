package com.learnspark.features.notification

/**
 * 阶段 3.3：通知系统桥接（按文档 §3.3）。
 *
 * 平台实现：
 * - Desktop：JDK AWT SystemTray（Windows/macOS/Linux GNOME 都支持）
 * - Android：FCM（Firebase Cloud Messaging，需 google-services.json + 服务端 FCM key）
 * - Email：通用 SMTP 发送（任意平台可用）
 *
 * 通知类型：
 * - 任务提醒（toast / 系统通知）
 * - 冲突副本（核心 UX）
 * - 徽章解锁（成就 + 积分）
 * - 连续打卡（streak 保持）
 */
interface Notifier {
    /**
     * 显示平台原生通知。
     * @param title 标题
     * @param body  正文
     * @param iconRes 平台相关图标资源（Desktop 暂忽略）
     */
    fun notify(title: String, body: String, iconRes: String? = null)

    /**
     * 邮件通知（跨平台一致）。
     * Desktop / Android 都通过服务端 /api/v1/notification/email 发送。
     */
    suspend fun sendEmail(to: String, subject: String, body: String): Boolean
}

/**
 * 通知管理器（高层 facade，组合多种通道）。
 *
 * 用法：
 *   notificationManager.notify("冲突副本", "已为您保留本地版本")
 *   notificationManager.notifyAchievement("初次启航", "提交第一份任务")
 */
class NotificationManager(
    private val notifier: Notifier,
) {
    fun notify(title: String, body: String) = notifier.notify(title, body)

    fun notifyConflict() = notify(
        title = "检测到冲突",
        body = "已为您保留本地版本为副本，可在知识库中查看",
    )

    fun notifyAchievement(badgeName: String, reason: String) = notify(
        title = "🎉 解锁徽章：$badgeName",
        body = reason,
    )

    fun notifyStreak(days: Int) = notify(
        title = "🔥 连续打卡 $days 天",
        body = "继续保持！你已超过 ${days - 1} 天的自己",
    )

    fun notifyParseReady(entryTitle: String) = notify(
        title = "知识库已就绪",
        body = "「$entryTitle」解析完成，可以查看",
    )

    suspend fun sendEmail(to: String, subject: String, body: String) =
        notifier.sendEmail(to, subject, body)
}
