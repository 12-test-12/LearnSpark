package com.learnspark.features.notification

/**
 * Desktop 通知：JDK AWT SystemTray。
 *
 * 注意：
 * - 仅在 SystemTray.isSupported() = true 时才创建（Linux headless 不可用）
 * - 占位图标：1x1 透明 PNG
 * - 点击通知：暂不处理（KMP Compose Desktop 与 AWT 事件循环集成较复杂，可后续扩展）
 */

/**
 * 供 Koin 直接绑定（阶段 3.3：Not 普通 Kotlin 路径调用，Koin 路径用 class 直引）。
 */
class DesktopAwtNotifier : Notifier {
    // 1x1 透明 PNG（base64 解码）
    private val transparentPng: ByteArray by lazy {
        java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
        )
    }

    private val trayIcon: java.awt.TrayIcon? by lazy {
        if (!java.awt.SystemTray.isSupported()) {
            return@lazy null
        }
        val tray = java.awt.SystemTray.getSystemTray()
        val image = java.awt.Toolkit.getDefaultToolkit().createImage(transparentPng)
        java.awt.TrayIcon(image, "LearnSpark").apply {
            isImageAutoSize = true
            toolTip = "LearnSpark"
            tray.add(this)
        }
    }

    override fun notify(title: String, body: String, iconRes: String?) {
        val ti = trayIcon ?: return
        ti.displayMessage(title, body, java.awt.TrayIcon.MessageType.INFO)
    }

    override suspend fun sendEmail(to: String, subject: String, body: String): Boolean {
        // 阶段 3.3：邮件通过后端 /api/v1/notification/email 发送（避免客户端配置 SMTP）
        // 此处仅返回 false 作为占位，待后端邮件端点落地后接通
        return false
    }
}
