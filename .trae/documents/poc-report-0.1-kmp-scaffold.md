# 阶段 0.1 KMP 双端脚手架 PoC 报告

> 日期：2026-07-22
> 依据：learnspark-multiplatform-rebuild-plan.md 第七章 阶段零 0.1

## 一、目标

KMP Wizard 跑通 Desktop + Android 双端 Hello World，验证：
- Gradle 配置
- Android 互操作
- Desktop JVM 打包
- 真实构建时间
- 遇到的所有坑

## 二、完成情况

| 验证点 | 状态 | 说明 |
|--------|------|------|
| KMP 项目骨架 | OK | learnspark-kmp/ 目录，17 个文件 |
| Gradle 配置评估 | OK | projects 任务识别 :composeApp 子模块 |
| Desktop 编译 | OK | compileKotlinDesktop 成功 |
| Desktop Uber Jar 打包 | OK | 28.88 MB，Main-Class: com.learnspark.MainKt |
| Android 互操作 | PENDING | 需安装 Android SDK |
| GitHub Actions CI | OK | desktop-build.yml 配置完成 |
| 真实构建时间 | OK | 已记录 |
| 坑点记录 | OK | 4 个坑已记录 |

## 三、真实构建时间

环境：Windows + JDK 17.0.17 (Microsoft-12574423) + 阿里云/腾讯云镜像

| 步骤 | 耗时 | 说明 |
|------|------|------|
| Gradle 8.7 下载（腾讯云镜像） | ~30s | 130 MB bin zip |
| gradlew help（首次） | 78.7s | 下载 + 解压 + daemon 启动 |
| gradlew projects（首次配置评估） | 14.8s | 插件元数据解析 |
| compileKotlinDesktop（首次编译） | 47s | 下载 Compose MP 运行时 + 编译 |
| packageUberJarForCurrentOS（首次打包） | 25.8s | flattenJars + Uber Jar |
| 增量构建（无改动） | ~5s | UP-TO-DATE |

## 四、构建产物

```
d:\LearnSpark\learnspark-kmp\composeApp\build\compose\jars\
  LearnSpark-windows-x64-1.0.0.jar   28.88 MB
```

- Main-Class: com.learnspark.MainKt
- 运行方式: java -jar LearnSpark-windows-x64-1.0.0.jar
- 包含: Compose MP 运行时 + Skiko 图形库 + 所有依赖

## 五、技术栈验证

| 组件 | 版本 | 验证结果 |
|------|------|---------|
| Gradle | 8.7 | OK |
| Kotlin | 2.0.0 | OK |
| Compose Multiplatform | 1.6.10 | OK |
| Android Gradle Plugin | 8.2.2 | OK（配置评估通过，未触发 Android 编译） |
| JDK | 17.0.17 | OK |
| compose.compiler 插件 | 2.0.0 | OK |

## 六、遇到的坑

### 坑 1: Write 工具在 Trae 沙箱环境下不能持久化文件

**现象**：用 Write 工具创建的文件（settings.gradle.kts、build.gradle.kts 等）在磁盘上找不到，但工具返回成功。

**原因**：Trae 沙箱环境对 Write 工具的文件写入有限制。

**解决**：改用 PowerShell `[System.IO.File]::WriteAllText($path, $content, $utf8)` 创建文件，配合 here-string `@'...'@` 避免变量插值。

### 坑 2: Gradle 下载超时

**现象**：`gradle-wrapper.properties` 默认 `networkTimeout=10000`（10 秒），从 services.gradle.org 下载 130 MB 的 Gradle 8.7 bin zip 超时。

**解决**：
1. 改用腾讯云镜像：`distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip`
2. 增加超时时间：`networkTimeout=120000`（120 秒）

### 坑 3: 缺少 android.useAndroidX=true

**现象**：`packageUberJarForCurrentOS` 任务在 `flattenJars` 步骤失败，报错：`Configuration ':composeApp:desktopRuntimeClasspath' contains AndroidX dependencies, but the 'android.useAndroidX' property is not enabled`

**原因**：Compose Multiplatform 的 desktop 端依赖传递引入了 AndroidX 库（collection、lifecycle、annotation），但项目缺少 `gradle.properties` 文件。

**解决**：创建 `gradle.properties`，添加：
```properties
android.useAndroidX=true
android.nonTransitiveRClass=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
kotlin.code.style=official
```

### 坑 4: Maven 依赖下载慢

**现象**：从 Google Maven 和 Maven Central 下载 Compose Multiplatform、Kotlin 编译器等依赖速度慢。

**解决**：在 `settings.gradle.kts` 的 `pluginManagement.repositories` 和 `dependencyResolutionManagement.repositories` 中添加阿里云镜像：
```kotlin
maven("https://maven.aliyun.com/repository/gradle-plugin")
maven("https://maven.aliyun.com/repository/public")
maven("https://maven.aliyun.com/repository/google")
maven("https://maven.aliyun.com/repository/central")
```

## 七、项目结构

```
learnspark-kmp/
+ .github/workflows/desktop-build.yml    GitHub Actions CI
+ composeApp/                            Compose Multiplatform 主模块
+   build.gradle.kts
+   src/
+     commonMain/kotlin/com/learnspark/
+       App.kt                           共享 UI（Android + Desktop）
+     androidMain/
+       AndroidManifest.xml
+       kotlin/com/learnspark/MainActivity.kt
+       res/values/{strings,themes}.xml
+     desktopMain/kotlin/com/learnspark/
+       Main.kt                          Desktop 入口
+ gradle/
+   libs.versions.toml                   版本目录
+   wrapper/gradle-wrapper.jar
+   wrapper/gradle-wrapper.properties
+ .gitignore
+ build.gradle.kts
+ gradle.properties                      AndroidX + JVM 内存配置
+ gradlew / gradlew.bat
+ README.md
+ settings.gradle.kts
```

## 八、Android 端 Blocker

当前环境未安装 Android SDK，无法验证 Android 端构建。

**需要安装**：
- Android SDK (compileSdk 34, minSdk 24, targetSdk 34)
- Android SDK Build-Tools 34.0.0
- Android Platform 34

**安装后验证命令**：
```powershell
cd d:\LearnSpark\learnspark-kmp
.\gradlew.bat :composeApp:assembleDebug
```

预期产物：`composeApp/build/outputs/apk/debug/composeApp-debug.apk`

## 九、下一步

1. **安装 Android SDK**，验证 Android 端构建（任务 0.1 收尾）
2. **阶段 0.2**: SQLDelight + Ktor 跨端 PoC（本地写记录 -> Pull 拉取 -> 显示）
3. **阶段 0.3**: JNA 系统 API + 降级方案 PoC（Windows DPAPI + Linux 降级）
4. 阶段零完成后，进入阶段一：KMP 项目正式化

## 十、决策点

按文档 10.1 节，阶段零末尾（第 2 周末）为决策点 M0。当前 Desktop 端 PoC 已通过，待 Android SDK 就绪后完成 Android 端 PoC，即可进入阶段一。