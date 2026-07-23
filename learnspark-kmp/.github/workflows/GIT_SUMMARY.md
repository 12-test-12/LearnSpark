# Git 信息总结

## 当前状态

- **分支**：`master`
- **本地领先远端**：1 commit（待 push）
- **远端**：`https://github.com/12-test-12/LearnSpark.git`
- **未跟踪文件**：`learnspark-kmp/data/`（数据库连接池缓存等，不应提交）

## 最新 commit

```
02689cf R5: 完成 KMP 重写 + 双端文件互通
```

**变更概览**：
- 删除：~210 个旧文件（Vue3 前端、Maven、Docker、老 Android patches、过期文档）
- 新增：~210 个新文件（KMP 客户端 + Kotlin Spring Boot 服务端 + Gradle wrapper + GitHub Actions CI）

## 历史 R 阶段 commits

| 阶段 | 描述 |
|---|---|
| R5 | 完成 KMP 重写 + 双端文件互通（当前） |
| R4 | 任务详情页上传 / AI 标注 / 多 AI provider / 多笔记格式解析 |
| R3 | 知识库多层文件夹 + 自定义通知 + 桌面托盘 + AI 整理 |
| R2 | AI 集成（DeepSeek + UserAiConfig）+ Submission 异步评审 |
| R1 | 后端服务与控制器（Project/Phase/Task/Sync/Submission/AI） |

## 推送命令

```bash
# 先查看要 push 的内容
git log -1 --stat

# 推送
git push origin master
```

## 推送后会触发

GitHub Actions 自动跑 `.github/workflows/ci.yml`：
1. 编译 server (`bootJar`)
2. 编译 Desktop (`compileKotlinDesktop`)
3. 打包 MSI (`packageMsi`)
4. 编译 Android (`compileDebugKotlinAndroid`)
5. 打包 APK (`assembleDebug`)
6. 把三个产物上传到 Artifacts

## 注意

- **CI 默认分支是 `main`**，但你的本地分支是 `master`。要么在 GitHub 上把默认分支改成 master，要么把 CI workflow 改成 `[ master ]`，要么本地 `git branch -M main` 改名。
- **data/ 目录在 .gitignore 里被排除**（数据库连接池、Flyway 历史、上传文件），不会被提交。
