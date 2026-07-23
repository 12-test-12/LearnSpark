# LearnSpark KMP 全平台客户端

阶段零 PoC 工程：Android + Desktop 双端 Compose Multiplatform Hello World

## 目录结构

learnspark-kmp/
+ composeApp/                       Compose Multiplatform 主模块
+   build.gradle.kts
+   src/
+     commonMain/kotlin/com/learnspark/
+       App.kt                      共享 UI
+     androidMain/kotlin/com/learnspark/
+       MainActivity.kt             Android 入口
+     desktopMain/kotlin/com/learnspark/
+       Main.kt                     Desktop 入口
+ gradle/
+   libs.versions.toml              版本目录
+   wrapper/
+ settings.gradle.kts
+ build.gradle.kts
+ gradlew / gradlew.bat
+ README.md

## 技术栈

- Kotlin 2.0.0
- Compose Multiplatform 1.6.10
- Gradle 8.7
- Android Gradle Plugin 8.2.2
- JDK 17

## 运行

Desktop 端: ./gradlew run
Android 端: ./gradlew assembleDebug （需 Android SDK）

## 国内镜像加速

- Gradle 下载: 腾讯云镜像
- Maven 依赖: 阿里云镜像