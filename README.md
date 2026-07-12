<div align="center">

# 📚 LearnSpark · 灵犀学习

**AI 驱动的学习计划 · 个人知识库 · 游戏化勉励系统**

[Vue 3] · [Spring Boot 3] · [MySQL 8] · [Capacitor 6]

</div>

---

# ⬇️ 下载 Android App

> **这是仓库首页最重要的内容** —— 三种方式任选一种，5 秒装到手机

## 🟢 方式一：一键下载（推荐）

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

<br><br>

**适合：日常使用**

文件：`learnspark-*.apk` · 约 8 MB

</td>
<td align="center" width="33%" valign="top">

### 🔧 最新构建版

<a href="../../releases">
  <img src="https://img.shields.io/badge/查看-所有版本-0969da?style=for-the-badge&logo=github&logoColor=white" alt="查看所有版本">
</a>

<br><br>

**适合：尝鲜测试**

每次 `git push main` 自动构建

</td>
<td align="center" width="33%" valign="top">

### 🛠️ 历史构建

<a href="../../actions/workflows/android.yml">
  <img src="https://img.shields.io/badge/Actions-构建历史-2ea44f?style=for-the-badge&logo=github-actions&logoColor=white" alt="Actions 构建历史">
</a>

<br><br>

**适合：开发者调试**

下载任意一次构建的产物

</td>
</tr>
</table>

> 📱 **手机用户**：直接点击「下载 Latest APK」→ 下载 `learnspark-*.apk` → 打开文件 → 允许"未知来源安装" → 完成
>
> 💻 **电脑用户**：点击按钮后跳转到 Releases 页面 → 找到 **Latest** 标记的版本 → 展开 Assets → 下载 `learnspark-*.apk` → 传到手机安装

---

## 🟡 方式二：扫码下载

> 把下面这张二维码发给你朋友 / 放到官网，扫码就能下载

<!-- 把 OWNER 替换成你的 GitHub 用户名 -->
<!-- 链接模板：https://github.com/OWNER/REPO/releases/latest/download/learnspark-VERSION.apk -->

**📷 生成二维码步骤：**

1. 访问 [qrcode-monkey.com](https://www.qrcode-monkey.com/)
2. 把下面的链接粘贴进去生成二维码：

```
https://github.com/OWNER/REPO/releases/latest
```

3. 把二维码图片保存到 `docs/qrcode.png`，并替换上面的 `OWNER/REPO` 为你的实际路径

---

## 🟢 方式三：命令行下载（高级用户）

**Linux / macOS：**
```bash
# 下载最新 release 的 APK（需要 jq + curl）
curl -sL "https://api.github.com/repos/OWNER/REPO/releases/latest" \
  | grep "browser_download_url.*\.apk" \
  | cut -d '"' -f 4 \
  | xargs curl -LO
```

**Windows PowerShell：**
```powershell
$url = (Invoke-RestMethod "https://api.github.com/repos/OWNER/REPO/releases/latest").assets `
       | Where-Object { $_.name -like "*.apk" } `
       | Select-Object -First 1 -ExpandProperty browser_download_url
Invoke-WebRequest -Uri $url -OutFile "learnspark.apk"
```

> ⚠️ 把 `OWNER/REPO` 替换成你的实际 GitHub 路径

---

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
- ✅ 今日任务进度
- 💬 每日一句金句
- 🌗 深色模式

</td>
</tr>
</table>

---

# 🏗️ 技术栈

| 层 | 技术 |
|---|---|
| **前端** | Vue 3.5 + Vite 5 + TypeScript + Naive UI + Pinia |
| **移动端** | Capacitor 6（Web 打包为 Android） |
| **后端** | Spring Boot 3.2 + Spring Data JPA + Flyway |
| **数据库** | MySQL 8.0 |
| **对象存储** | MinIO |
| **搜索引擎** | Elasticsearch 8 |
| **AI** | DeepSeek API |

---

# 🚀 本地开发

```bash
# 1. 启动后端
docker compose up -d mysql
./mvnw spring-boot:run

# 2. 启动前端（新终端）
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

详细文档：
- 📖 [Android 打包发布指南](docs/ANDROID_BUILD.md)
- 🔧 [常见问题排查](docs/TROUBLESHOOTING.md)
- 📖 [数据库设计](数据库设计.md)
- 📖 [任务流程拆分](任务流程拆分.md)
- 📖 [项目任务书](任务书.md)

---

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

| 触发 | 产物 | 在仓库主页能看到吗？ |
|---|---|---|
| `git push origin main` | APK + AAB → 自动 Release（`build-{sha}`） | ✅ 能（Latest 标签） |
| `git push origin v*` | APK + AAB → 正式 Release（`v*`） | ✅ 能（Latest 标签） |
| Pull Request | 仅 Actions Artifact | ❌ 需去 Actions 页面 |
| 手动 Run workflow | 同 push main | ✅ 能（Latest 标签） |

---

# 🤝 贡献

欢迎 PR 和 Issue！

# 📄 License

[MIT](LICENSE)
