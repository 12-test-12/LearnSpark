import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            // Coroutines
            implementation(libs.kotlin.coroutines.core)

            // Compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // SQLDelight
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            // Ktor (commonMain - core + content negotiation + auth + serialization)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)

            // 序列化
            implementation(libs.kotlinx.serialization.json)

            // DI / 导航 / 日志 / KV
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.tab.navigator)
            implementation(libs.voyager.transitions)
            implementation(libs.kermit)
            implementation(libs.multiplatform.settings)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
            implementation("androidx.datastore:datastore-preferences:1.1.1")
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.security.crypto)
            implementation(libs.multiplatform.settings.datastore)
            implementation(libs.koin.android)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlin.coroutines.swing)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.sqldelight.jdbc.driver)
            implementation(libs.ktor.client.cio)

            // JNA for Desktop secure keystore
            implementation(libs.jna)
            implementation(libs.jna.platform)

            // R8：嵌入 Spring Boot 服务端，Desktop 启动时同时启动服务端
            // 手机端通过局域网 IP 连接 Desktop 的 8080 端口即可同步
            // 注意：:server 模块的 Spring 依赖是 implementation（不传递），所以这里要显式声明
            // spring-boot-starter-web 会传递依赖 spring-boot（含 SpringApplication）+ spring-context + spring-web
            implementation(project(":server"))
            implementation(libs.spring.boot.starter.web)
            implementation(libs.spring.boot.starter.data.jpa)
            runtimeOnly(libs.h2.database)
        }
    }
}

sqldelight {
    databases {
        create("LearnSparkDb") {
            packageName.set("com.learnspark.db")
        }
    }
}

android {
    namespace = "com.learnspark"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildToolsVersion = "36.0.0"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.learnspark"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // 阶段 3.4：Release 签名（密钥通过环境变量 / keystore.properties 注入）
    signingConfigs {
        create("release") {
            // keystore.properties 模板（不提交到 git）：
            //   storeFile=/path/to/learnspark.keystore
            //   storePassword=...
            //   keyAlias=learnspark
            //   keyPassword=...
            val keystoreProps = rootProject.file("keystore.properties")
            if (keystoreProps.exists()) {
                val p = Properties()
                keystoreProps.inputStream().use { p.load(it) }
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            } else {
                // Debug 模式 / CI 中 keystore.properties 缺失时：跳过 release 签名
                println("[signing] keystore.properties not found, release build will use debug signing")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (rootProject.file("keystore.properties").exists()) {
                signingConfigs.getByName("release")
            } else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "com.learnspark.MainKt"

        nativeDistributions {
        targetFormats(TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
        packageName = "LearnSpark"
        packageVersion = "1.0.0"

        // 阶段 R1：显式声明需要的 JDK 模块，避免 JPackage 静态裁剪后找不到
        // java.sql.*（被 SQLDelight JDBC driver 反射加载）
        modules("jdk.crypto.ec", "java.sql", "java.naming", "java.management")

        windows {
            menuGroup = "LearnSpark"
            upgradeUuid = "8a7b3c2d-1e4f-4a5b-9c8d-7e6f5a4b3c2d"
            // R8-图标：EXE 资源（多尺寸 .ico）
            iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
        }
        macOS {
            bundleID = "com.learnspark"
            // R8-图标：DMG 资源（多尺寸 .icns）
            iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
        }
        linux {
            packageName = "learnspark"
            // R8-图标：DEB 资源（256×256 png）
            iconFile.set(project.file("src/desktopMain/resources/icon-256.png"))
        }
    }
    }
}