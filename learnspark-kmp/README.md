# learnspark-kmp

LearnSpark 的 KMP 客户端 + Spring Boot 服务端单体仓库。完整功能、部署、技术栈说明见仓库根目录的 [`README.md`](file:///d:/LearnSpark/README.md)。

## 目录

```
learnspark-kmp/
├── composeApp/            # Compose Multiplatform 客户端（Android + Desktop）
├── server/                # Spring Boot 服务端（Kotlin）
├── deploy/                # MySQL / Nginx / 备份脚本
├── docker-compose.yml     # mysql + app-api + app-worker + nginx
├── .env.example
├── gradle/libs.versions.toml
└── README.md
```

## 客户端入口

- 桌面端：`composeApp/src/desktopMain/kotlin/com/learnspark/Main.kt`
- Android 端：`composeApp/src/androidMain/kotlin/com/learnspark/MainActivity.kt`
- 共享 UI：`composeApp/src/commonMain/kotlin/com/learnspark/App.kt`

## 服务端入口

- 启动类：`server/src/main/kotlin/com/learnspark/server/LearnSparkApplication.kt`
- 配置文件：`server/src/main/resources/application.yml` + `application-worker.yml`
- 容器构建：`server/Dockerfile`

## 常用命令

```bash
# 客户端
./gradlew :composeApp:run                    # 桌面端运行
./gradlew :composeApp:assembleDebug          # Android Debug APK
./gradlew :composeApp:packageExe             # Windows EXE
./gradlew :composeApp:packageDeb             # Linux DEB
./gradlew :composeApp:createDistributable    # 桌面可运行目录（用来打 tar.gz）

# 服务端
./gradlew :server:bootRun                    # 本地直接跑（需本机 MySQL）
./gradlew :server:bootJar                    # 打 Fat JAR
docker compose up -d                         # 跑完整栈
```
