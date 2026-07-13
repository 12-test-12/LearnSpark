<div align="center">

# 📚 LearnSpark · 灵犀学习

**AI 驱动的学习计划 · 个人知识库 · 游戏化勉励系统**

\[Vue 3] · \[Spring Boot 3] · \[MySQL 8] · \[Capacitor 6]

</div>

***

# 📱 平台支持

| 平台          | 状态     | 说明                           |
| ----------- | ------ | ---------------------------- |
| **Android** | ✅ 已支持  | 当前主要发布平台，所有功能均已适配            |
| iOS         | 🔜 规划中 | 基于 Capacitor 6，架构已预留，待后续适配   |
| Web         | 🔜 规划中 | 前端基于 Vue 3 + Vite，可独立部署为 PWA |

> 当前版本专注于 **Android 端**的完整体验。项目基于 Capacitor 6 构建，前端与原生能力通过 Capacitor 插件桥接，架构上已预留多端扩展能力，后续可平滑迁移到 iOS 与独立 Web 部署。

***

# ⬇️ 下载 Android App

> **这是仓库首页最重要的内容** —— 两种方式任选一种，快速装到手机

## 一键下载（推荐）

> **点击下方按钮，直接下载最新版 APK**

<table>
<tr>
<td align="center" width="33%" valign="top">

### 📦 最新稳定版

<!-- GitHub 自动把 ../../releases/latest 解析成 https://github.com/<owner>/<repo>/releases/latest -->

<!-- 用户无需手动替换占位符 -->

<a href="../../releases/latest">
  <img src="https://img.shields.io/badge/下载-Latest%20APK-18a058?style=for-the-badge&logo=android&logoColor=white" alt="下载最新 APK">
</a>

<br />

**适合：日常使用**

文件：`learnspark-*.apk` · 约 8 MB

</td>
<td align="center" width="33%" valign="top">

### 🔧 最新构建版

<a href="../../releases">
  <img src="https://img.shields.io/badge/查看-所有版本-0969da?style=for-the-badge&logo=github&logoColor=white" alt="查看所有版本">
</a>

<br />

**适合：尝鲜测试**

每次 `git push main` 自动构建

</td>
<td align="center" width="33%" valign="top">

### 🛠️ 历史构建

<a href="../../actions/workflows/android.yml">
  <img src="https://img.shields.io/badge/Actions-构建历史-2ea44f?style=for-the-badge&logo=github-actions&logoColor=white" alt="Actions 构建历史">
</a>

<br />

**适合：开发者调试**

下载任意一次构建的产物

</td>
</tr>
</table>

> 📱 **手机用户**：直接点击「下载 Latest APK」→ 下载 `learnspark-*.apk` → 打开文件 → 允许"未知来源安装" → 完成
>
> 💻 **电脑用户**：点击按钮后跳转到 Releases 页面 → 找到 **Latest** 标记的版本 → 展开 Assets → 下载 `learnspark-*.apk` → 传到手机安装

***

<br />

# ✨ 项目功能

<table>
<tr>
<td width="50%" valign="top">

### 🤖 AI 学习计划

- 📂 上传文档（`.md` / `.txt`）
- ⏱️ 设置目标天数
- 🌐 可选联网搜索最新资料
- 🧠 DeepSeek 自动拆解为可执行任务

</td>
<td width="50%" valign="top">

### 📚 个人知识库

- 📝 学习过程中自动沉淀
- 🔍 全文检索（基于 MySQL ngram）
- 🏷️ 标签 + 阶段分类
- 📤 支持导入/导出

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🎮 游戏化勉励

- 🔥 连续打卡
- 🏆 成就徽章
- ⭐ 积分系统
- 🎉 Confetti 动效

</td>
<td width="50%" valign="top">

### 📊 数据仪表盘

- 📅 90 天热力图
- ✅ 待办任务进度
- 💬 每日一句金句
- 🌗 深色模式

</td>
</tr>
</table>

***

# 🏗️ 技术栈

| 层        | 技术                                               |
| -------- | ------------------------------------------------ |
| **前端**   | Vue 3.5 + Vite 5 + TypeScript + Naive UI + Pinia |
| **移动端**  | Capacitor 6（Web 打包为 Android 原生应用）                |
| **本地存储** | SQLite（sql.js，离线运行，无需数据库服务）                      |
| **AI**   | DeepSeek API                                     |

> **后端代码保留（未启用）**：`src/` 目录保留了 Spring Boot 3.2 + Spring Data JPA + Flyway + MySQL 8.0 的完整实现，用于未来多端数据同步扩展。当前 Android 端纯前端离线运行，不依赖后端。
>
> **多端扩展架构**：前端与原生能力完全通过 Capacitor 插件桥接，业务代码不耦合特定平台。未来新增 iOS 端时，只需 `npx cap add ios` 并补充 iOS 签名配置，无需改动 Vue 业务代码；独立 Web 部署时直接使用 `npm run build` 产物即可；需要多端数据同步时启用保留的后端代码即可。

***

# 🚀 本地开发

> 当前 Android 端为**纯前端离线架构**，数据存储在设备本地 SQLite（sql.js），无需启动后端和数据库即可运行。

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

> 📌 后端代码（`src/` 目录、Spring Boot + MySQL + Flyway）已保留，用于未来多端数据同步扩展。当前前端不依赖后端。

详细文档：

- 📖 [Android 打包发布指南](docs/ANDROID_BUILD.md)
- 📖 [Android 安装问题修复](docs/ANDROID_INSTALL_FIX.md)
- 🔧 [常见问题排查](docs/TROUBLESHOOTING.md)
- 📖 [数据库设计](数据库设计.md)
- 📖 [任务流程拆分](任务流程拆分.md)
- 📖 [项目任务书](任务书.md)

***

# 📦 发布新版本

### 一键脚本（推荐）

```bash
# Windows PowerShell
.\release.ps1 v1.0.0

# Linux / macOS / WSL
./release.sh v1.0.0
```

### 手动发布

```bash
git tag v1.0.0
git push origin v1.0.0
```

### 触发规则

| 触发                     | 产物                                    | 在仓库主页能看到吗？      |
| ---------------------- | ------------------------------------- | --------------- |
| `git push origin main` | APK + AAB → 自动 Release（`build-{sha}`） | ✅ 能（Latest 标签）  |
| `git push origin v*`   | APK + AAB → 正式 Release（`v*`）          | ✅ 能（Latest 标签）  |
| Pull Request           | 仅 Actions Artifact                    | ❌ 需去 Actions 页面 |
| 手动 Run workflow        | 同 push main                           | ✅ 能（Latest 标签）  |

***

# 🤝 贡献

欢迎 PR 和 Issue！

# 📄 License

[MIT](LICENSE)
