# LearnSpark 构建指南（GitHub Actions）

无需本地配置，**push 即可构建**。

## 触发条件

| 事件 | 触发内容 |
|---|---|
| Push 到 `main` / `develop` 或 Pull Request | 完整 CI：server 编译 + Desktop 编译 + MSI 打包 + Android 编译 + APK 打包 |
| Push tag `v*` （如 `v1.0.0`） | 在 `Actions` 跑完后自动创建 GitHub Release，附带所有产物 |

## 产物

CI 跑完后在 `Actions → 选 run → Artifacts` 下载：

```
learnspark-server          server-1.0.0.jar        (后端 Spring Boot)
learnspark-desktop-msi     LearnSpark-1.0.0.msi    (Windows 桌面客户端)
learnspark-android-debug   app-debug.apk           (Android 调试包)
```

## 怎么发版本

```bash
git tag v1.0.0
git push origin v1.0.0
```

会自动创建 Release：`https://github.com/<你的用户名>/LearnSpark/releases/tag/v1.0.0`

## 本地调试（可选）

```bash
# 跑测试
./gradlew.bat :server:bootJar
./gradlew.bat :composeApp:packageMsi
./gradlew.bat :composeApp:assembleDebug
```

## 注意事项

- **JDK 17 固定**（Spring Boot 3.4 不支持 JDK 21+，更不支持 24）
- **Windows runner**：因为 MSI 打包必须 Windows
- **Android SDK 由 `android-actions/setup-android` 自动装**，不需要 secrets
- **无 Docker**：完全按用户要求，CI 镜像里跑 Gradle 直出产物
- **MySQL 不会跑**（CI 阶段只编译不连库，运行时由你自己起 MySQL）
