# LearnSpark（灵犀学习）项目介绍

> 一款 AI 驱动的学习计划 + 个人知识库 + 勉励系统

---

## 一、产品概述

### 1.1 一句话定义

**LearnSpark（灵犀学习）** 是一款融合"AI 辅助学习路线规划、智能成果审核、个人知识沉淀、提醒与勉励"四大能力的 PWA Web 应用，帮助学习者形成「**计划 → 执行 → 沉淀 → 正向反馈**」的成长闭环。

### 1.2 核心价值

| 维度 | 能力 |
|------|------|
| 🎯 **主动规划** | AI 结合网络资源与个人资料生成定制化学习路线 |
| ✅ **执行监督** | 每日任务提交后 AI 自动审核，给出评分与鼓励性评语 |
| 📚 **知识沉淀** | 上传资料与提交总结自动解析入库，支持全文搜索与双向链接 |
| ⏰ **提醒激励** | 邮件通知、打卡日历、成就徽章等情感化设计 |

### 1.3 产品差异化

- **用户自带 AI Key**：所有用户自主配置 DeepSeek API Key，本地存储或服务端 AES-256-GCM 加密存储，零平台运营成本。
- **闭环学习系统**：不只是任务清单，更是从规划到沉淀的完整学习操作系统。
- **完全自主可控**：技术栈基于 Vue3 + Spring Boot 主流开源方案，可私有化部署。

---

## 二、技术架构

### 2.1 整体架构图

```
┌──────────────────────────────────────────────────────────────┐
│                      Vue 3 前端 (PWA)                          │
│   Naive UI · Pinia · Vue Router · Vite · Axios · Canvas      │
└────────────────────────┬─────────────────────────────────────┘
                         │ RESTful API (JSON / multipart)
┌────────────────────────┴─────────────────────────────────────┐
│                  Spring Boot 3.2.5 后端 (单体模块化)             │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────────┐ │
│  │  auth    │   plan   │   ai     │knowledge │  gamification│ │
│  │ 认证模块 │ 计划模块  │ AI 网关  │ 知识库模块│   成就模块    │ │
│  ├──────────┼──────────┼──────────┼──────────┼──────────────┤ │
│  │submission│notification│ security │ common  │  knowledge   │ │
│  │ 审核模块  │  通知模块  │  安全模块 │ 公共组件 │  文件解析     │ │
│  └──────────┴──────────┴──────────┴──────────┴──────────────┘ │
└────────────────────────┬─────────────────────────────────────┘
                         │ JPA + Flyway
┌────────────────────────┴─────────────────────────────────────┐
│ 数据层: MySQL 8.0 · 本地文件存储 · 外部 AI/搜索 API           │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 技术栈明细

| 层级 | 技术 | 说明 |
|------|------|------|
| **前端** | Vue 3.5 + Vite 5 + TypeScript | PWA、Composition API、自动导入 |
| | Naive UI 2.39 | 主题定制、深色模式、毛玻璃组件 |
| | Pinia 3 + pinia-plugin-persistedstate | 状态管理 + 持久化 |
| | vue-router 4 + axios | 路由与 HTTP 拦截 |
| | canvas-confetti + marked + dompurify | 动画 / Markdown 渲染 / XSS 防护 |
| **后端** | Spring Boot 3.2.5 + Java 17 | 主框架 |
| | Spring Data JPA + Hibernate | ORM |
| | Flyway 9 | 数据库版本化迁移 |
| | Spring Security + JWT (jjwt 0.12.5) | 认证授权 |
| | RestClient + Lombok | HTTP 调用与代码简化 |
| | Apache Flexmark 0.64.8 | Markdown 解析 |
| **数据层** | MySQL 8.0 (utf8mb4_0900_ai_ci) | 主存储 |
| | 本地文件系统 / MinIO 8.5.10 | 文件存储（可切换） |
| **AI 集成** | DeepSeek API (deepseek-chat / deepseek-reasoner) | 路线生成 + 成果审核 |
| | Bing Web Search API v7 | 网络资料检索 |
| **部署** | Docker Compose | MySQL + MinIO + Elasticsearch 一键拉起 |
| | Vite PWA + Capacitor 6 | PWA + Android 移动端（可扩展） |

### 2.3 模块化包结构

后端采用"按业务模块分包"的设计，便于后续按需拆分为微服务：

```
com.learnspark
├── auth/           # 用户认证（注册、登录、JWT 签发）
├── plan/           # 学习项目、阶段、任务 CRUD
├── submission/     # 任务提交与 AI 审核
├── ai/             # AI 网关
│   ├── dto/        # DeepSeekConfig / SearchConfig
│   ├── entity/     # UserAiConfig
│   ├── plan/       # 路线生成（DeepSeekPlanClient + PlanGenerationService）
│   ├── search/     # 网络搜索（BingSearchService + SearchQueryService）
│   └── controller/ # AI 配置管理
├── knowledge/      # 知识库（条目 + 双向链接 + 文件存储）
├── gamification/   # 成就系统（积分、徽章、统计仪表盘）
├── notification/   # 邮件提醒（定时任务 + 模板）
├── common/         # 公共组件（异常、安全、加密、JWT、Web 配置）
└── LearnSparkApplication.java
```

---

## 三、核心功能模块

### 3.1 用户与认证

- **注册 / 登录**：邮箱 + 密码，BCrypt 加密存储，JWT 无状态会话（24h 过期）
- **本地模式**：用户可选择 AI Key 仅存在浏览器，不上传服务端（功能降级提示）
- **JWT 鉴权**：Bearer Token + `CurrentUser` 注解自动注入用户 ID

### 3.2 学习计划（plan 模块）

- **学习项目（Project）**：用户创建一个学习目标项目（含目标、每日时长、AI 生成标志）
- **阶段（Phase）**：项目可拆分为多个学习阶段，含目标说明
- **任务（Task）**：每个阶段包含若干每日任务，含描述、验证标准、截止日期、状态
- **状态机**：`pending` → `submitted` → `passed` / `failed`
- **AI 生成标记**：`is_ai_generated` 字段区分手动创建与 AI 生成

### 3.3 AI 审核（submission 模块）

**流程**：
1. 用户提交任务总结（文本 + 可选附件）
2. 服务端组装 Prompt：任务描述 + 验证标准 + 用户总结
3. 调用 DeepSeek（用户 Key）返回 `{ passed, score, feedback }`
4. 审核通过 → 任务完成 + 积累积分 + 撒花动画
5. 审核未通过 → 显示改进建议，可重新提交
6. 提交内容自动沉淀为知识库条目（`source_type='submission'`）

### 3.4 知识库（knowledge 模块）

- **文件上传**：支持 .md / .txt 格式（后续可扩展 PDF/Word）
- **自动解析**：标题提取、纯文本提取、字数统计
- **存储方式**：
  - 元数据 + 纯文本 → MySQL
  - 原始文件 → 本地目录（`./uploads`）或 MinIO（可配置）
- **双向链接**：解析 Markdown 中的 `[[笔记名]]`，自动建立笔记间关联
- **搜索**：基于 SQL `LIKE` 的全文检索（已索引 `LOWER(title)`）

### 3.5 AI 智能生成学习路线（ai/plan 模块）

**核心特性**：上传 .md/.txt 资料 + 学习目标 → AI 生成结构化阶段+任务清单

**完整流程**：
```
用户上传文件 ──┐
              ├─→ 解析纯文本 ──┐
用户输入目标 ──┘                │
                              ├─→ 构建 Prompt ──→ DeepSeek API
[Bing 搜索可选] ──→ 生成关键词 ─┘                      │
                                                      ▼
                                解析 JSON 阶段+任务 ──→ 写入数据库
                                                      │
                                                      ▼
                                          返回生成结果（含 ID）
```

**关键设计**：
- 独立 `DeepSeekPlanClient`（readTimeout 60s，专为长 prompt 优化）
- 失败降级：搜索失败 → 跳过搜索；AI 解析失败 → 抛 AI_GENERATE_FAILED
- `replaceMode` 支持替换旧路线（自动清理旧 phases/tasks）
- 整个流程 `@Transactional` 保证数据一致性

### 3.6 网络搜索增强（ai/search 模块）

- **`BingSearchService`**：调用 Bing Web Search API v7，最多返回 5 条结果
- **`SearchQueryService`**：先用 DeepSeek 根据学习目标生成 3-5 个搜索关键词，再逐一调用 Bing
- 降级：未配置搜索 Key → 跳过；搜索失败 → 跳过（不阻断主流程）

### 3.7 AI 配置管理（ai 模块）

- **Key 加密存储**：AES-256-GCM 模式，每次加密生成随机 12 字节 IV
- **Key 脱敏显示**：前端仅显示后 4 位（`****abcd`）
- **本地/服务端双模式**：
  - `localMode=true` → Key 不上传，仅前端使用，服务端功能降级
  - `localMode=false` → Key AES 加密后存数据库，多设备共享
- **配置测试**：内置连通性测试，发起最小化 DeepSeek 请求验证 Key 有效性

### 3.8 成就与积分（gamification 模块）

- **积分（UserScore）**：用户 × 项目维度，累计点数 + 连续打卡天数
- **徽章（Badge + UserBadge）**：成就解锁（连续 N 天、累计 N 任务等）
- **数据仪表盘（Dashboard）**：
  - 今日待完成数 / 连续打卡天数 / 总积分
  - 7 日学习热力图（按天聚合提交记录）
  - AI 每日一句（DeepSeek 生成激励语）
  - 项目统计（完成率、阶段进度）

### 3.9 邮件提醒（notification 模块）

- **每日提醒**：用户设置提醒时间 + 邮箱，定时任务扫描今日待完成用户
- **模板内容**：使用 Thymeleaf 模板，包含今日任务列表 + 昨日 AI 鼓励语
- **节流保护**：每天最多发送一次，记录 `last_sent_at` 避免重复
- **可启用/禁用**：用户可独立控制提醒开关

---

## 四、数据库设计

数据库采用 MySQL 8.0（utf8mb4_0900_ai_ci 字符集），全部使用 UUID 主键、Flyway 管理版本化迁移。

### 4.1 核心表清单（共 15 张）

| 模块 | 表名 | 用途 |
|------|------|------|
| **认证** | `users` | 用户基础信息 |
| **AI** | `user_ai_config` | 用户 AI 配置（加密 Key、本地模式开关） |
| **计划** | `projects` | 学习项目 |
| | `phases` | 学习阶段 |
| | `tasks` | 每日任务 |
| **审核** | `submissions` | 任务提交记录（含 AI 评分） |
| **知识库** | `knowledge_entries` | 知识条目（笔记/上传资料） |
| | `knowledge_links` | 笔记间双向链接（复合主键） |
| **成就** | `user_scores` | 用户项目积分（@IdClass 复合主键） |
| | `badges` | 徽章定义 |
| | `user_badges` | 用户徽章关联（@IdClass 复合主键） |
| **通知** | `reminder_settings` | 用户提醒配置 |
| **公共** | `flyway_schema_history` | Flyway 迁移历史 |

### 4.2 关键设计决策

- **软删除**：核心业务表（`projects` / `tasks` / `knowledge_entries` / `submissions`）使用 `deleted_at DATETIME(3) DEFAULT NULL` + Hibernate `@SQLRestriction`
- **复合主键**：成就模块的 `user_scores` / `user_badges` 使用 `@IdClass` 复合主键
- **唯一索引**：`users.email` 唯一约束 + `LOWER(email)` 函数索引（不区分大小写登录）
- **CHECK 约束**：`users.status IN ('active','disabled')`、`tasks.status IN ('pending','submitted','passed','failed')`
- **枚举用 VARCHAR**：避免迁移时锁表

### 4.3 迁移脚本结构

```
src/main/resources/db/migration/
└── V1__init.sql    # 初始建表（15 张表 + 索引 + 约束）
```

---

## 五、API 设计

所有 API 以 `/api/v1` 为前缀，使用 JWT 鉴权，统一返回 `ApiResult<T>` 包装结构：

```json
{ "code": 0, "message": "ok", "data": { ... }, "success": true }
```

### 5.1 主要端点

| 模块 | 端点 | 方法 | 说明 |
|------|------|------|------|
| **认证** | `/auth/register` | POST | 注册 |
| | `/auth/login` | POST | 登录 |
| | `/auth/me` | GET | 当前用户信息 |
| **AI 配置** | `/user/ai-config` | GET / PUT | 查询/更新 AI 配置 |
| | `/user/ai-config/test` | POST | 测试 Key 连通性 |
| **学习计划** | `/projects` | GET / POST | 项目列表 / 创建 |
| | `/projects/{id}` | GET / PUT / DELETE | 项目详情/编辑/删除 |
| | `/projects/{id}/phases` | GET / POST | 阶段列表/创建 |
| | `/projects/{id}/tasks` | GET | 任务列表（支持按日期过滤） |
| **AI 生成** | `/projects/{id}/generate-plan` | POST | **AI 智能生成路线**（multipart 上传） |
| **任务提交** | `/tasks/{id}/submit` | POST | 提交任务总结（AI 审核） |
| | `/tasks/{id}/submissions` | GET | 历史提交记录 |
| **知识库** | `/knowledge` | GET | 搜索/列表 |
| | `/knowledge/upload` | POST | 上传文件 |
| | `/knowledge/{id}` | GET / DELETE | 详情/删除 |
| **统计** | `/stats/dashboard` | GET | 仪表盘数据 |
| | `/stats/projects/{id}` | GET | 项目统计 |
| | `/user/activity` | GET | 活动热力图 |
| **提醒** | `/user/reminder` | GET / PUT | 提醒配置 |

### 5.2 错误码体系

`ErrorCode` 枚举使用分段编码：

| 段 | 模块 | 示例 |
|----|------|------|
| 1xxxx | 通用 | `PARAM_ERROR(10001)` |
| 2xxxx | 认证 | `AUTH_FAILED(20001)` |
| 3xxxx | AI | `AI_CONFIG_NOT_SET(30001)` / `AI_KEY_INVALID(30003)` / `AI_GENERATE_FAILED(30004)` / `SEARCH_FAILED(30005)` |
| 4xxxx | 业务资源 | `PROJECT_NOT_FOUND` / `TASK_NOT_FOUND` |
| 5xxxx | 业务异常 | `TASK_ALREADY_SUBMITTED` |
| 10xxx+ | HTTP 状态 | `INTERNAL_ERROR(50000)` / `UNAUTHORIZED(40100)` |

---

## 六、前端设计

### 6.1 路由结构

```
/login              → 登录
/register           → 注册
/dashboard          → 仪表盘（默认首页）
/projects           → 项目列表
/projects/:id       → 项目详情
/projects/:id/generate-plan → AI 生成路线向导
/tasks/:id/submit   → 任务提交与审核
/knowledge          → 知识库浏览
/knowledge/:id      → 知识条目详情
/achievements       → 成就墙
/settings           → 设置（AI 配置 + 提醒配置）
```

### 6.2 核心页面

| 页面 | 关键交互 |
|------|---------|
| **登录/注册** | VS Code Dark 主题 `#1e1e1e`，玻璃拟态卡片 |
| **仪表盘** | 顶部 KPI 卡片（待完成/连续/积分）+ 7 日热力图 + AI 每日一句 |
| **项目详情** | 项目信息 + 阶段时间轴（Naive UI NCollapse）+ 任务卡片 + AI 生成入口 |
| **AI 生成向导** | 5 步 NSteps：目标时长 → 上传资料 → 搜索开关 → 生成中 → 结果预览 |
| **任务提交** | Markdown 编辑器（marked 渲染） + AI 审核结果卡片 + 撒花动画 |
| **知识库** | 搜索栏（关键词） + 笔记列表（标题/摘要/标签） + 详情 Markdown 渲染 |
| **设置** | Tabs：AI 配置（Key 输入 + 测试） + 提醒配置（邮箱 + 时间 + 时区） |

### 6.3 设计系统

- **深色模式优先**：所有页面支持 `light` / `dark` / `auto` 三种模式
- **品牌色**：`#18a058`（Naive UI 绿色，作为成功/激活色）
- **代码编辑风格**：登录/注册采用 VS Code Dark `#1e1e1e` 配色，营造"开发者工具"氛围
- **状态颜色**：
  - `pending` 灰色
  - `submitted` 蓝色
  - `passed` 绿色
  - `failed` 红色
- **动画**：canvas-confetti（任务通过撒花）、Naive UI 过渡动画

---

## 七、安全设计

| 维度 | 措施 |
|------|------|
| **密码** | BCrypt 加盐哈希存储 |
| **JWT** | HS512 签名，24h 过期，生产环境通过 `APP_JWT_SECRET` 注入 |
| **AI Key** | AES-256-GCM 加密存储，每次随机 IV，密文格式 `Base64(IV‖ciphertext+authTag)` |
| **前端显示** | AI Key 脱敏仅显示后 4 位（`****abcd`） |
| **SQL 注入** | 全 JPA 参数化查询 |
| **XSS** | 前端 DOMPurify 净化 Markdown 渲染 |
| **CORS** | 通过 `CorsProperties` 精确配置允许源 |
| **本地模式** | 用户可拒绝 Key 上传，前端提示功能降级 |

---

## 八、部署与运维

### 8.1 一键启动

```bash
# 1. 启动基础设施（MySQL + MinIO + Elasticsearch）
docker-compose up -d

# 2. 启动后端
cd d:\LearnSpark
mvn spring-boot:run

# 3. 启动前端
cd frontend
npm install
npm run dev
```

### 8.2 关键环境变量

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `DB_USER` / `DB_PASSWORD` | MySQL 凭据 | `root` / `123456` |
| `APP_JWT_SECRET` | JWT 签名密钥（≥32 字符） | dev 默认值 |
| `APP_AES_SECRET` | AI Key 加密主密钥 | dev 默认值 |
| `APP_MINIO_*` | MinIO 连接配置 | 见 application.yml |
| `APP_STORAGE_TYPE` | 文件存储类型 `local` / `minio` | `local` |
| `APP_STORAGE_LOCAL_DIR` | 本地存储目录 | `./uploads` |

### 8.3 数据库迁移

所有表结构变更通过 Flyway 脚本管理：
- `V1__init.sql` — 初始建表
- `V2__xxx.sql` — 后续增量变更
- 启用 `spring.flyway.baseline-on-migrate: true` 兼容已有库

---

## 九、项目结构

```
LearnSpark/
├── src/                          # 后端 Spring Boot
│   ├── main/
│   │   ├── java/com/learnspark/   # 业务代码（按模块分包）
│   │   └── resources/
│   │       ├── application.yml    # 共享配置
│   │       ├── application-dev.yml # dev 环境配置（MySQL）
│   │       ├── db/migration/      # Flyway 迁移脚本
│   │       └── templates/email/   # 邮件模板
│   └── test/                     # 单元测试
├── frontend/                     # 前端 Vue 3
│   ├── src/
│   │   ├── api/                  # API 封装（每个模块一个文件）
│   │   ├── views/                # 页面组件
│   │   ├── components/           # 公共组件
│   │   ├── stores/               # Pinia 状态
│   │   ├── router/               # 路由
│   │   ├── layouts/              # 布局
│   │   └── App.vue
│   ├── package.json
│   └── vite.config.ts
├── docker-compose.yml            # 基础设施编排
├── Dockerfile                    # 后端镜像
├── pom.xml                       # Maven 配置
├── init.sql                      # 数据库初始数据
├── 任务书.md                      # 产品需求文档
├── 任务流程拆分.md                 # 任务分解
├── 数据库设计.md                   # 数据库基准文档
└── README.md                     # 本文档
```

---

## 十、发展路线

| 阶段 | 状态 | 主要工作 |
|------|------|---------|
| **阶段一** 核心 MVP | ✅ 已完成 | 认证 / 计划 CRUD / 任务提交审核 / 最小知识库 / 邮件提醒 / 部署 |
| **阶段二** 智能生成 | ✅ 已完成 | Bing 搜索集成 / DeepSeek 路线生成 / 文件解析 |
| **阶段三** 体验打磨 | ✅ 已完成 | 成就系统 / 数据仪表盘 / 撒花动画 / 深色模式 / 统计热力图 |
| **阶段四** 协作与生态 | 📋 规划中 | 学习小组 / 移动端 App / RAG 知识库问答 / 多模态文件解析 |

---

## 十一、适用人群

✅ **自主学习者**：想用 AI 辅助规划学习路径、坚持每日打卡  
✅ **技术博主**：想沉淀学习笔记并形成双向链接的个人知识库  
✅ **教师/培训师**：需要按项目分组织学习计划，统计完成情况  
✅ **AI 兴趣者**：希望低门槛体验 DeepSeek API 集成的实际项目参考  
✅ **开源贡献者**：基于 MIT 风格的模块化代码二次开发

---

## 十二、开源协议

本项目仅供学习交流使用。生产部署前请：

- 修改 `APP_JWT_SECRET` / `APP_AES_SECRET` 为强随机值
- 启用 HTTPS
- 配置 CORS 白名单
- 接入专业邮件服务商（避免被识别为垃圾邮件）
- 评估 DeepSeek / Bing API 的费用与配额

---

**LearnSpark · 让学习有迹可循，让成长有迹可循。** ✨
