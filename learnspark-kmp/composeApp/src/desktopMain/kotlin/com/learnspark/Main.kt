package com.learnspark

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.learnspark.core.di.coreModule
import com.learnspark.core.di.platformModule
import com.learnspark.core.security.runKoinPoC
import com.learnspark.core.security.runSecureKeystorePoC
import org.koin.core.context.startKoin

fun main() {
    when (System.getProperty("learnspark.poc")) {
        "keystore" -> {
            val code = runSecureKeystorePoC()
            kotlin.system.exitProcess(code)
        }
        "koin" -> {
            val code = runKoinPoC()
            kotlin.system.exitProcess(code)
        }
    }

    // 阶段 1.1.6：启动 Koin
    startKoin {
        modules(coreModule, platformModule())
    }

    application {
        Window(onCloseRequest = ::exitApplication, title = "LearnSpark") {
            App()
        }
    }
}

actual fun currentPlatform(): String = "Desktop (JVM, KMP)"
