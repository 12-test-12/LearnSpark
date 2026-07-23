package com.learnspark.core.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.learnspark.core.security.DesktopPlatformTokenStore
import com.learnspark.core.security.PlatformTokenStore
import com.learnspark.db.LearnSparkDb
import com.learnspark.features.notification.DesktopAwtNotifier
import com.learnspark.features.notification.Notifier
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop 平台 Koin module。
 *
 * - PlatformTokenStore: JDK Preferences（明文，由 EncryptedTokenStore 加密）
 * - LearnSparkDb: JdbcSqliteDriver (H2/SQLite embedded)
 * - Notifier: 阶段 3.3 AWT SystemTray
 */
actual fun platformModule(): Module = module {
    single<PlatformTokenStore> { DesktopPlatformTokenStore() }
    single<LearnSparkDb> {
        val driver = JdbcSqliteDriver("jdbc:sqlite:learnspark.db")
        LearnSparkDb.Schema.create(driver)
        LearnSparkDb(driver)
    }
    // 阶段 3.3：通知（Desktop = AWT SystemTray）
    single<Notifier> { DesktopAwtNotifier() }
}
