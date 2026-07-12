# 📱 Android 打包部署指南

本项目使用 **Capacitor 6 + GitHub Actions** 将 Vue 3 前端打包为 Android 原生应用。
构建产物自动发布到 **GitHub Releases**，用户可直接在仓库主页下载安装。

---

## 🔄 触发方式

| 触发条件 | 产物 |
|---|---|
| `git push origin main` | APK 上传为 **Artifact**（Actions 页面下载） |
| `git push origin v1.0.0` | APK + AAB 上传为 Artifact **并自动创建 GitHub Release** |
| 手动触发（Actions 页面 → Run workflow） | 仅 Artifact |

---

## ⚙️ 首次配置（仅需一次）

### 1. 生成签名 Keystore

> Android 要求所有 Release APK 必须签名。同一个 keystore 的签名才能在用户设备上覆盖升级，**一旦丢失将无法更新已发布的应用**，请妥善保管！

#### 在本地（Windows PowerShell）生成：

```powershell
# 进入 frontend/android 目录
cd d:\LearnSpark\frontend\android

# 生成 keystore（有效期 10000 天 ≈ 27 年）
keytool -genkeypair -v `
  -keystore learnspark.keystore `
  -alias learnspark `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass YOUR_STORE_PASSWORD `
  -keypass YOUR_KEY_PASSWORD `
  -dname "CN=LearnSpark,O=YourCompany,L=City,C=CN"
```

> ⚠️ 把 `YOUR_STORE_PASSWORD` / `YOUR_KEY_PASSWORD` 换成你自己的强密码，并**记到密码管理器**！

#### Base64 编码 keystore（用于 CI）：

```powershell
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("learnspark.keystore")) | Out-File -Encoding ASCII learnspark.keystore.b64
Get-Content learnspark.keystore.b64
```

复制输出内容（很长的一段字符）。

### 2. 在 GitHub 仓库配置 Secrets

进入 GitHub 仓库页面 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**：

| Secret 名称 | 值 | 必填 |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | 上一步复制的 base64 字符串 | ✅ |
| `KEYSTORE_PASSWORD` | 生成 keystore 时的 `-storepass` 密码 | ✅ |
| `KEY_ALIAS` | 这里是 `learnspark`（与 `-alias` 一致） | ✅ |
| `KEY_PASSWORD` | 生成 keystore 时的 `-keypass` 密码 | ✅ |
| `VITE_API_BASE_URL` | 生产后端 API 地址（如 `https://api.learnspark.com`） | ⭕ 可选 |

> 📌 Secrets 是加密的，公开仓库也看不到明文。

---

## 🚀 发布新版本

### 日常构建（仅供测试）

```bash
git push origin main
# → 等待 3-5 分钟，Actions 页面下载 Artifact
```

### 发布正式版本（推荐流程）

```bash
# 1. 提交代码
git add .
git commit -m "feat: 新增 xxx 功能"
git push origin main

# 2. 创建并推送 tag
git tag v1.0.0
git push origin v1.0.0

# 3. GitHub Actions 自动：
#    - 构建 APK + AAB
#    - 创建 GitHub Release
#    - 附带自动生成的更新日志
```

> 推送 tag 后，**仓库主页右侧的 Releases 区块**会自动出现最新版本。

---

## 📥 用户下载方式

### 方式 1：GitHub Releases（推荐）

- 仓库主页 → 右侧 **Releases** 区域 → 点击最新版本 → 下载 `learnspark-1.0.0.apk`
- 或直接访问：`https://github.com/<OWNER>/<REPO>/releases/latest`

### 方式 2：README 下载徽章

在 `README.md` 顶部添加：

```markdown
<!-- 把 <OWNER>/<REPO> 替换为你的 GitHub 用户名/仓库名 -->
## 📱 下载 Android App

[![Latest Release](https://img.shields.io/github/v/release/<OWNER>/<REPO>?style=for-the-badge&logo=android&label=下载)](https://github.com/<OWNER>/<REPO>/releases/latest)
[![Download APK](https://img.shields.io/github/downloads/<OWNER>/<REPO>/total/learnspark-1.0.0.apk?style=for-the-badge&logo=github&label=总下载量)](https://github.com/<OWNER>/<REPO>/releases/latest)
```

### 方式 3：手机扫码下载

把 Release 页面的 APK 下载链接生成二维码放到 README / 官网，用户扫码即可下载。

---

## 🔧 安装到 Android 设备

### 用户首次安装（推荐）

由于 APK 未上架应用商店，需要在手机开启"未知来源安装"：

1. 下载 `learnspark-x.x.x.apk`
2. 点击 APK 文件
3. 系统提示 → 允许"此来源安装应用"
4. 安装完成

### 后续覆盖升级

- 同一 keystore 签名的新版本 APK 可直接覆盖安装（不会丢数据）

---

## 🛠️ 本地调试（开发者）

```bash
# 1. 安装 Capacitor
cd frontend
npm install

# 2. 构建前端（必须用 --base=./ 相对路径）
npm run build:android

# 3. 添加 Android 平台（首次）
npx cap add android

# 4. 同步 Web 资源
npm run cap:sync

# 5. 用 Android Studio 打开
npm run cap:open:android

# 6. 在 Android Studio 中点击 Run ▶️
```

或纯命令行：

```bash
cd frontend/android
./gradlew assembleDebug
# APK 输出：app/build/outputs/apk/debug/app-debug.apk
```

---

## 📁 关键文件清单

| 文件 | 作用 |
|---|---|
| `.github/workflows/android.yml` | GitHub Actions 构建流程 |
| `frontend/capacitor.config.ts` | Capacitor 全局配置（appId / appName / webDir） |
| `frontend/package.json` | 新增 `@capacitor/*` 依赖与 `build:android` 脚本 |
| `frontend/android/` | Capacitor 生成的原生工程（**不入库**，CI 首次自动生成） |
| `frontend/android/app/build.gradle` | Gradle 构建脚本（CI 注入签名配置） |
| `frontend/android/app/src/main/AndroidManifest.xml` | Android 清单（自动生成） |

---

## ❓ 常见问题

### Q1：CI 提示 "Keystore was tampered with, or password was incorrect"
**A**：检查 `KEYSTORE_PASSWORD` / `KEY_PASSWORD` Secret 是否与生成 keystore 时一致。

### Q2：APK 安装后打开是空白页
**A**：检查 `VITE_API_BASE_URL` Secret 是否正确；或确认 `capacitor.config.ts` 中没有错误地填写了 `server.url` 指向了不可达的地址。

### Q3：Gradle 构建报 "SDK location not found"
**A**：CI 端已通过 `android-actions/setup-android@v3` 自动处理；本地请在 `frontend/android/local.properties` 写入 `sdk.dir=/path/to/Android/sdk`。

### Q4：升级 Capacitor 版本
修改 `frontend/package.json` 中 `@capacitor/*` 的版本号后，CI 会自动重新生成 Android 工程。**注意**：Capacitor 6 → 7 升级时 `appId` 不变，但 `compileSdk` 等可能有差异，请参考 [官方迁移指南](https://capacitorjs.com/docs/main/updating/6-0)。

### Q5：想上架 Google Play
**A**：上传 `app-release.aab`（CI 已生成）到 [Google Play Console](https://play.google.com/console)。需要的额外步骤：Google Play 应用签名（Play App Signing），需在 Play Console 后台注册。

### Q6：能在 iOS 上也打包吗？
**A**：可以加 Capacitor iOS 平台，但 iOS 构建**必须在 macOS** 上执行（Apple 限制）。需要新增 `macos-latest` runner 的 workflow。
