package com.learnspark

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.learnspark.core.di.coreModule
import com.learnspark.core.di.platformModule
import com.learnspark.core.di.setAndroidContext
import com.learnspark.core.files.ActivityHolder
import com.learnspark.core.files.FilePicker
import com.learnspark.core.files.IntentLauncherHolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    // 阶段 R3b：API 33+ 动态申请 POST_NOTIFICATIONS
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 不处理：NotImpl 内部 catch */ }

    // 阶段 R4c：OpenDocument 文件选择回调
    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            FilePicker.dispatchResult(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 阶段 3.1：注入 Android Context 供 LegacyDataLocator 使用
        setAndroidContext(this)
        // 阶段 R4c：注册 Activity + 文件选择 launcher
        ActivityHolder.current = this
        IntentLauncherHolder.launcher = { types -> openDocumentLauncher.launch(types) }

        // 阶段 1.1.6：启动 Koin
        startKoin {
            androidContext(this@MainActivity)
            modules(coreModule, platformModule())
        }

        // 阶段 R3b：首次启动时申请通知权限
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ActivityHolder.current = null
        IntentLauncherHolder.launcher = null
    }
}

actual fun currentPlatform(): String = "Android (KMP)"
