package com.learnspark.core.di

import com.learnspark.core.security.AndroidPlatformTokenStore
import com.learnspark.core.security.PlatformTokenStore
import com.learnspark.db.LearnSparkDb
import com.learnspark.features.notification.AndroidNotificationNotifier
import com.learnspark.features.notification.Notifier
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android 平台 Koin module。
 *
 * - PlatformTokenStore: DataStore Preferences（明文，由 EncryptedTokenStore 加密）
 * - LearnSparkDb: AndroidSqliteDriver
 * - Notifier: 阶段 3.3 FCM 占位
 */
actual fun platformModule(): Module = module {
    single<PlatformTokenStore> { AndroidPlatformTokenStore(androidContext()) }
    single<LearnSparkDb> {
        LearnSparkDb(
            app.cash.sqldelight.driver.android.AndroidSqliteDriver(
                LearnSparkDb.Schema,
                androidContext(),
                "learnspark.db"
            )
        )
    }
    // 阶段 R3b：通知（Android = NotificationManagerCompat + Channel）
    single<Notifier> { AndroidNotificationNotifier(androidContext()) }
}
