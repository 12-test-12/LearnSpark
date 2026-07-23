package com.learnspark.features.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.learnspark.MainActivity
import com.learnspark.R

/**
 * R3b：Android 平台通知实现。
 *
 * - 创建 NotificationChannel（API 26+）
 * - 使用 NotificationManagerCompat 发送本地通知
 * - 点击通知 → 跳转到 MainActivity
 * - POST_NOTIFICATIONS 权限：API 33+ 动态申请（已在 MainActivity 处理）
 */
class AndroidNotificationNotifier(private val context: Context) : Notifier {

    init {
        createChannel()
    }

    override fun notify(title: String, body: String, iconRes: String?) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Logger.withTag("Notifier").w { "POST_NOTIFICATIONS not granted, skip notify" }
                return
            }
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            title.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(title.hashCode(), notification)
        } catch (e: SecurityException) {
            // API 33+ 用户拒绝：catch 异常，log 后忽略
            Logger.withTag("Notifier").w { "notify SecurityException: ${e.message}" }
        }
    }

    override suspend fun sendEmail(to: String, subject: String, body: String): Boolean {
        // 阶段 R3b：邮件通过后端 /api/v1/notification/email 发送
        return false
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LearnSpark 提醒",
                AndroidNotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "用户自定义的提醒通知"
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "learnspark_reminder"
    }
}
