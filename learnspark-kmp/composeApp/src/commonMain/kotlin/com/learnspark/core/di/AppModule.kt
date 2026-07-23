package com.learnspark.core.di

import com.learnspark.core.files.FileCache
import com.learnspark.core.network.apiBaseUrl
import com.learnspark.core.security.EncryptedTokenStore
import com.learnspark.core.security.PlatformTokenStore
import com.learnspark.core.security.SecureStorage
import com.learnspark.core.security.TokenStore
import com.learnspark.core.security.createSecureStorage
import com.learnspark.data.api.LearnSparkApi
import com.learnspark.data.db.KnowledgeFolderRepository
import com.learnspark.data.db.ProjectRepository
import com.learnspark.data.db.ReminderRepository
import com.learnspark.data.migration.LegacyDataLocator
import com.learnspark.data.migration.MigrationService
import com.learnspark.data.migration.MigrationViewModel
import com.learnspark.data.sync.SyncManager
import com.learnspark.db.LearnSparkDb
import com.learnspark.features.knowledge.KnowledgeViewModel
import com.learnspark.features.notification.NotificationManager
import com.learnspark.features.notification.ReminderViewModel
import com.learnspark.features.settings.AiConfigScreen
import com.learnspark.features.settings.AiConfigViewModel
import com.learnspark.features.tasks.TaskDetailViewModel
import com.learnspark.features.viewer.FileViewerViewModel
import com.learnspark.shared.components.DashboardViewModel
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * LearnSpark 通用 Koin module。
 *
 * - SecureStorage: 平台 expect/actual，注入由 [createSecureStorage] 工厂提供
 * - TokenStore: 装饰器 [EncryptedTokenStore]，依赖 SecureStorage + PlatformTokenStore
 * - LearnSparkApi: HTTP 客户端，baseUrl 来自 [apiBaseUrl]
 * - ProjectRepository: 业务仓库，依赖 SQLDelight db
 * - MigrationService / MigrationViewModel: 阶段 3.1 旧版数据迁移
 * - KnowledgeFolderRepository / ReminderRepository: 阶段 R3 双端共享仓库
 * - KnowledgeViewModel / ReminderViewModel: R3 屏幕 ViewModel
 *
 * 平台相关绑定（PlatformTokenStore / LearnSparkDb driver）由各 source set
 * 中的 [platformModule] 提供并合并。
 */
val coreModule: Module = module {
    single<SecureStorage> { createSecureStorage() }
    single<TokenStore> { EncryptedTokenStore(get(), get<PlatformTokenStore>()) }
    single<LearnSparkApi> { LearnSparkApi(apiBaseUrl(), get()) }
    single<ProjectRepository> { ProjectRepository(get<LearnSparkDb>()) }

    // 阶段 R3a：知识库文件夹仓库
    single { KnowledgeFolderRepository(get<LearnSparkDb>()) }
    // 阶段 R3b：提醒仓库（settings + logs）
    single { ReminderRepository(get<LearnSparkDb>()) }

    // 阶段 3.1：旧版数据迁移
    single { LegacyDataLocator() }
    // multiplatform-settings 的 Settings 既是接口又是顶层工厂函数，Kotlin 无法直接调用 Settings() 构造
    // 使用 typealias 别名绕过
    single<Settings> { com.learnspark.core.di.createSettings() }
    single {
        MigrationService(
            api = get(),
            locator = get(),
            // 阶段 1.2 JWT 接入后，从 TokenStore 解析 userId。
            // 现阶段 dev 环境用 V1 预置账号固定 ID 00000000-0000-0000-0000-000000000001
            userIdProvider = { "00000000-0000-0000-0000-000000000001" },
        )
    }
    single { MigrationViewModel(get(), get(), get()) }

    // 阶段 3.3：通知系统
    // Notifier 由各 platformModule 实际提供（expect/actual），避免 commonMain 引用 expect fun
    single { NotificationManager(get()) }

    // 阶段 R3a：知识库 ViewModel
    single { KnowledgeViewModel(get(), get()) }
    // 阶段 R3b：提醒与通知 ViewModel（共享一个实例 → 启动后台轮询）
    single { ReminderViewModel(get(), get(), get(), notifier = get()) }

    // 阶段 R4a：AI 配置 ViewModel
    single { AiConfigViewModel(get()) }
    // 阶段 R4c：任务详情 ViewModel（上传 + AI 标注可参考文章）
    single { TaskDetailViewModel(get(), get()) }

    // 阶段 R5c：跨端文件缓存
    single { FileCache() }
    // 阶段 R5d：文件查看器 ViewModel
    single { FileViewerViewModel(get(), get()) }

    // 阶段 R6a：仪表盘聚合 ViewModel
    single { DashboardViewModel(get(), get()) }

    // R8：跨端数据同步管理器
    single { SyncManager(get(), get(), get(), get()) }
}

/**
 * multiplatform-settings 工厂函数封装。
 *
 * 原因：top-level `Settings()` 函数与同名接口冲突，
 *      Kotlin 编译器优先解析为构造调用 → "interface has no constructors"。
 *      用独立函数包一层即可。
 */
expect fun createSettings(): Settings

/**
 * 占位：平台 module 将在 androidMain / desktopMain 中提供并被合并到最终 Koin 配置。
 *
 * 期望平台 module 至少注册：
 * - `single<PlatformTokenStore> { ... }`
 * - `single<LearnSparkDb> { ... }`
 */
expect fun platformModule(): Module
