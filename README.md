<div align="center">

# 📚 LearnSpark 灵犀学习

**AI 驱动的学习计划 · 个人知识库 · 游戏化勉励系统**

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Vue 3](https://img.shields.io/badge/Vue-3.5-42b883?logo=vue.js)](https://vuejs.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6db33f?logo=spring-boot)](https://spring.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)

---

## 📱 下载 Android App

<!-- ↓↓↓ 把 OWNER/REPO 替换成你的 GitHub 用户名/仓库名，例如 patter/LearnSpark ↓↓↓ -->

| 渠道 | 下载 | 说明 |
|---|---|---|
| 🟢 **稳定版（推荐）** | [![Latest](https://img.shields.io/github/v/release/OWNER/REPO?style=for-the-badge&logo=android&label=Latest%20APK&color=18a058)](https://github.com/OWNER/REPO/releases/latest) | 生产环境用这个 |
| 🟡 **开发版** | [![Nightly](https://img.shields.io/badge/Nightly-自动构建-blue?style=for-the-badge&logo=github-actions)](https://github.com/OWNER/REPO/releases?q=nightly&expanded=true) | 尝鲜用，可能有 bug |

> 📲 手机用户：直接点上面的 **Latest APK** 徽章 → 下载 `.apk` → 允许"未知来源安装" → 完成
> 💻 电脑用户：在仓库主页右侧 **Releases** 区域点最新版本 → 下载 `learnspark-x.x.x.apk`

---

</div>

## ✨ 核心功能

- 🤖 **AI 学习计划生成** — 上传文档（`.md` / `.txt`）+ 目标天数 + 可选联网搜索 → DeepSeek 自动拆解成可执行的每日任务
- 📚 **个人知识库** — 学习过程中沉淀的知识点自动入库，支持全文检索
- 🎮 **游戏化勉励** — 积分、连续打卡、成就徽章、Confetti 动效
- 📊 **学习仪表盘** — 90 天热力图 + 今日任务 + 每日一句
- 🌗 **深色模式** — 默认暗色，护眼 + 科技感

## 🏗️ 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5 + Vite 5 + TypeScript + Naive UI + Pinia |
| 移动端 | Capacitor 6（Web 打包 Android） |
| 后端 | Spring Boot 3.2 + Spring Data JPA + Flyway |
| 数据库 | MySQL 8.0 |
| 对象存储 | MinIO |
| 搜索引擎 | Elasticsearch 8 |
| AI | DeepSeek API（学习计划生成） |

## 🚀 本地开发

### 1. 启动后端

```bash
# 1. 启动 MySQL
docker compose up -d mysql

# 2. 启动 Spring Boot
./mvnw spring-boot:run
```

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

### 3. 完整文档

- 📖 [Android 打包发布指南](docs/ANDROID_BUILD.md)
- 📖 [数据库设计](数据库设计.md)
- 📖 [任务流程拆分](任务流程拆分.md)
- 📖 [项目任务书](任务书.md)

## 📦 发布新版本

### 一键脚本（推荐）

```bash
# Windows PowerShell
.\release.ps1 v1.0.0

# Linux / macOS
./release.sh v1.0.0
```

脚本会自动：校验版本号格式 → 创建 tag → 推送 → 打开浏览器监控 CI → 构建完成后打开 Release 页面。

### 手动发布

```bash
git tag v1.0.0
git push origin v1.0.0
# → 等待 3-5 分钟，访问仓库 Releases 页面下载
```

### 触发规则

| 触发 | 产物 | 渠道 |
|---|---|---|
| `git push origin main` | APK + AAB → **Nightly Prerelease** | 仓库 Releases 页面（标"Pre-release"） |
| `git push origin v*` | APK + AAB → **Stable Release** | 仓库 Releases 页面（标"Latest"） |
| PR | 仅构建不发布 | Actions Artifact |
| 手动触发 | 同 push main | 同上 |

## 📱 安装 Android App

1. 访问 [Releases 页面](../../releases/latest) 下载最新 APK
2. 在 Android 设备上点击 APK 文件
3. 系统提示 → 允许"此来源安装应用"
4. 安装完成

> ⚠️ 由于应用未上架 Google Play / 国内应用商店，首次安装需手动开启"未知来源安装"权限。后续升级可直接覆盖安装（同一签名）。

## 🤝 贡献

欢迎 PR 和 Issue！

## 📄 License

[MIT](LICENSE)
