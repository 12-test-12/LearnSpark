package com.learnspark

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.learnspark.core.di.coreModule
import com.learnspark.core.di.platformModule
import com.learnspark.core.security.runKoinPoC
import com.learnspark.core.security.runSecureKeystorePoC
import com.learnspark.server.LearnSparkApplication
import org.koin.core.context.startKoin
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

/**
 * R8：Desktop 嵌入式服务端启动器。
 *
 * 在 Desktop 应用启动时同时启动 Spring Boot 服务端（使用 H2 文件数据库），
 * 手机端通过局域网 IP 连接即可实现双端数据同步。
 *
 * 不需要安装 MySQL，不需要单独运行服务端。
 * 将来部署到云服务器时，单独运行 server 模块 + MySQL 即可。
 *
 * 复用 server 模块的 LearnSparkApplication（已带 @EnableAsync / @EnableScheduling /
 * @ConfigurationPropertiesScan），避免重复定义丢失注解。
 */
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

    // R8：启动嵌入式 Spring Boot 服务端（H2 文件数据库，不需要 MySQL）
    val springContext: ConfigurableApplicationContext? = try {
        SpringApplication.run(LearnSparkApplication::class.java, "--spring.profiles.active=local")
    } catch (e: Exception) {
        System.err.println("[LearnSpark] 嵌入式服务端启动失败：${e.message}")
        System.err.println("[LearnSpark] 应用将以离线模式运行，数据仅存本地。")
        null
    }

    // 阶段 1.1.6：启动 Koin
    startKoin {
        modules(coreModule, platformModule())
    }

    application {
        Window(
            onCloseRequest = {
                // 退出时关闭 Spring Boot
                springContext?.close()
                exitApplication()
            },
            title = "LearnSpark",
            icon = painterResource("icon.png"),
        ) {
            App()
        }
    }
}

actual fun currentPlatform(): String = "Desktop (JVM, KMP)"
