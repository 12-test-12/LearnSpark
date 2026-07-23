# LearnSpark 全平台重构方案

> **目标**：Android + Desktop（Windows/macOS/Linux）双端原生客户端 + Web 端保留现有 Vue3 PWA，统一后端、共享业务模型、离线优先、冲突副本式同步 + 友好 UX、企业级安全。
>
> **技术基底**：Kotlin Multiplatform（KMP）共享业务逻辑 + Compose Multiplatform 原生 UI + SQLDelight + Ktor
>
> **选型原则**：稳定优先于先进、可交付优先于炫技、阶段验证先行于全面铺开、技术冲突翻译为用户语言

---

## 一、现状分析与重构决策

### 1.1 现有技术栈盘点

| 层 | 现状 | 处理 | 理由 |
|----|------|------|------|
| 后端框架 | Spring Boot 3.2.5 + JPA + Flyway | 保留 + 拆分双进程 | 业务 + 解析物理隔离 |
| 数据库 | MySQL 8.0 | 保留 + Flyway V2/V3/V4 | schema 设计合理 |
| AI 集成 | DeepSeek + Bing Search | 保留 | 云端 API 稳定 |
| 文件解析 | Flexmark（仅 .md/.txt） | 增量：后端加 Tika + PDFBox + Tesseract | 用户已要求 |
| 认证 | JWT 单令牌 + BCrypt | 增强：加 RefreshToken + 设备表 | 低成本升级 |
| 部署 | Docker Compose | 保留 + 拆分 app-api / app-worker | 进程隔离 |
| Web 前端 | Vue 3 + Vite + Naive UI + sql.js | 保留 + 增强 | 重写为 KMP Web 风险太高 |
| Android | Capacitor 6.x 打包 Vue | 重写为 KMP Android 原生工程 | 业务逻辑统一 |
| iOS | — | 不做 | 编译需 Mac、PoC 风险最高 |
| Desktop | — | 新增：KMP Desktop（JVM）打包 | 用户要求 |

### 1.2 为什么选 KMP 双端（Android + Desktop）

| 维度 | Vue3 + Capacitor（现状） | Flutter | KMP 双端（推荐） |
|------|--------------------------|---------|---------------------|
| 与后端同语言 | TS（不同） | Dart（不同） | Kotlin（同） |
| 与后端共享代码 | 无 | 弱 | 强（commonMain） |
| Desktop 端支持 | 不支持 | 需 Flutter Desktop | JVM 成熟 |
| Android 端 | WebView 模拟 | Skia 自绘 | Compose 自绘 |
| 原生互操作 | JS Bridge | FFI 受限 | 直接调用 SDK |
| 包体积 | 大（WebView + JS） | 中 | 小（编译产物） |
| iOS 风险 | — | 需 Swift 互操作 | 砍掉，无 iOS 风险 |

**结论**：砍 iOS 后，KMP 在 Android + Desktop 双端是最务实的选择——后端 Kotlin 工程师可直接上手，没有 iOS 编译环境包袱，没有 Swift 互操作复杂度。

### 1.3 重写范围

```
保留（不动）              新写（交付目标）               延后到 v4
─────────────────────────────────────────────────────────────
后端 Spring Boot           KMP commonMain（业务逻辑）    iOS 端（v4 评估）
MySQL + Flyway             KMP Desktop（Compose MP）    端侧 AI 推理
Vue 3 Web 前端             KMP Android 原生工程          真正的双向同步（CRDT）
现有 Docker Compose        SQLDelight 本地数据库          Web 端 KMP Wasm
DeepSeek 集成              Ktor 客户端                    端到端加密同步
现有后端 Service           后端双进程（app-api + app-worker）
                           JNA 系统 API 安全存储
                           设备表 + RefreshToken
                           冲突副本同步引擎
                           Tika 后端集成（独立进程）
                           旧版数据迁移脚本
```

---

## 二、目标架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                  LearnSpark 客户端体系                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────┐  ┌──────────────────────────────────────────┐ │
│  │  Web 端（保留）   │  │    KMP 双端客户端（核心）                  │ │
│  │  Vue 3 + PWA     │  │  ┌─────────────────────────────────────┐ │ │
│  │  + sql.js         │  │  │  commonMain（业务逻辑 ~75% 共享）     │ │ │
│  │  + IndexedDB     │  │  │  ┌────────┬────────┬────────┐         │ │ │
│  │                  │  │  │  │  认证  │ 计划   │ 知识库  │        │ │ │
│  │  （独立维护，     │  │  │  │ 状态机 │ 离线CRUD│ 仓储层  │       │ │ │
│  │   不在 KMP 中）   │  │  │  └────────┴────────┴────────┘         │ │ │
│  │                  │  │  │  ┌────────┬────────┬────────┐         │ │ │
│  │                  │  │  │  │ AI抽象 │ 同步   │ 配置    │        │ │ │
│  │                  │  │  │  │ 云端   │ 冲突副本│ 多平台   │       │ │ │
│  │                  │  │  │  │ Provider│ 引擎↑↓ │ settings │       │ │ │
│  │                  │  │  │  └────────┴────────┴────────┘         │ │ │
│  │                  │  │  │  ┌─────────────────────────────────┐ │ │ │
│  │                  │  │  │  │  SQLDelight (SQLite)            │ │ │ │
│  │                  │  │  │  │  离线全功能 + 冲突副本表        │ │ │ │
│  │                  │  │  │  └────────────┬────────────────────┘ │ │ │
│  │                  │  │  └───────────────┼──────────────────────┘ │ │
│  │                  │  │                  │                         │ │
│  │                  │  │  ┌───────────────┼──────────────────────┐ │ │
│  │                  │  │  │  Ktor Client（HTTP + Auth 插件）     │ │ │
│  │                  │  │  │  + RefreshToken 自动刷新              │ │ │
│  │                  │  │  └───────────────┬──────────────────────┘ │ │
│  │                  │  │                  │                         │ │
│  │                  │  │  expect 声明（仅双端必要）：             │ │ │
│  │                  │  │    expect fun secureStorage()           │ │ │
│  │                  │  │    expect fun biometricAuth()           │ │ │
│  │                  │  │    expect fun createSqlDriver()         │ │ │
│  │                  │  │    expect fun pushNotifier()            │ │ │
│  │                  │  │    expect fun systemTray()              │ │ │
│  │                  │  └──────┬──────────────────┬──────────────┘ │ │
│  │                  │         │                  │                │ │
│  │                  │    ┌────┴────────┐    ┌─────┴──────────┐     │ │
│  │                  │    │  Desktop   │    │   Android      │     │ │
│  │                  │    │   JVM      │    │                │     │ │
│  │                  │    │ JNA→DPAPI/ │    │ Keystore       │     │ │
│  │                  │    │ libsecret  │    │ BiometricPrompt│     │ │
│  │                  │    │ JdbcSql    │    │ AndroidSqlite  │     │ │
│  │                  │    │ AWT Tray   │    │ FCM            │     │ │
│  │                  │    └────────────┘    └────────────────┘     │ │
│  └──────────────────┘  └──────────────────────────────────────────┘ │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ HTTPS（REST + 同步端点）
┌──────────────────────────────┴──────────────────────────────────────┐
│               Spring Boot 后端（双进程架构）                          │
│                                                                      │
│  ┌──────────────────────────┐     ┌──────────────────────────────┐ │
│  │   app-api.jar（API 服务） │     │  app-worker.jar（解析进程）  │ │
│  │   -Xmx512m                │     │   -Xmx1024m                  │ │
│  │  ┌──────────┬──────────┐ │     │  ┌────────────────────────┐ │ │
│  │  │  auth    │  plan    │ │     │  │   Tika + PDFBox        │ │ │
│  │  │ +Refresh │ 同步端点  │ │     │  │   + Tesseract OCR      │ │ │
│  │  │ +设备表  │          │ │     │  │   内存隔离，不影响 API  │ │ │
│  │  └──────────┴──────────┘ │     │  └────────────────────────┘ │ │
│  │  ┌──────────┬──────────┐ │     │           │                  │ │
│  │  │  ai      │ knowledge│ │     │  @Scheduled 定时轮询        │ │
│  │  │ Provider │ 上传入口  │ │     │  (fixedDelay = 5000ms)     │ │
│  │  │ 云端代理 │ 写 PENDING│←┼─────┼────────────SELECT WHERE       │ │
│  │  └──────────┴──────────┘ │     │           status=PENDING     │ │
│  │                          │     │           ↓ 解析完成          │ │
│  │   业务端口 8080          │     │   UPDATE status=READY        │ │
│  └──────────────────────────┘     └──────────────────────────────┘ │
│                          │                                          │
│  ┌───────────────────────┴────────────────────────────────────┐   │
│  │  MySQL 8.0（主数据 + 设备表 + RefreshToken 黑名单表）       │   │
│  │  —— 不引入 Redis，无消息队列，靠 DB 状态机实现进程间通信     │   │
│  └────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 技术栈定稿

| 层 | 技术 | 版本 | 维护方 | 说明 |
|----|------|------|--------|------|
| **跨端共享** | Kotlin Multiplatform | K2 / 2.0+ | JetBrains + Google | commonMain 75%+ 代码 |
| **双端 UI** | Compose Multiplatform | 1.8+ | JetBrains | Desktop Stable / Android Stable |
| **本地数据库** | SQLDelight | 2.0+ | Square / Block | 强类型 SQL，迁移工具完善 |
| **HTTP 客户端** | Ktor Client | 3.0+ | JetBrains | KMP 全平台，Auth 插件 + RefreshToken 自动刷新 |
| **JSON 序列化** | kotlinx.serialization | 1.7+ | JetBrains | 编译期生成 |
| **协程** | kotlinx.coroutines | 1.9+ | JetBrains | — |
| **依赖注入** | Koin | 4.0+ | Insert-Koin | KMP 主流 DI，无反射 |
| **导航** | Voyager | 1.1+ | Adriel Cafe | Compose MP 主流导航 |
| **配置存储** | multiplatform-settings | 1.2+ | russhwolf | 配合 SecureStorage |
| **日志** | Kermit | 2.0+ | Touchlab | KMP 主流日志 |
| **Markdown 渲染** | compose-markdown | 0.5+ | jeziellago | — |
| **图表** | Koalaplot | 0.6+ | Koalaplot | 仪表盘 |
| **平台安全存储** | expect/actual | — | 平台原生 | Android Keystore / Desktop JNA→系统 API |
| **平台生物识别** | expect/actual | — | 平台原生 | Android BiometricPrompt / Desktop 系统 PIN |
| **平台推送/通知** | expect/actual | — | 平台原生 | Android FCM / Desktop AWT SystemTray |
| **Desktop 系统 API** | JNA | 5.13+ | Java Native Access | Windows DPAPI / Linux libsecret / macOS Keychain |
| **后端 API** | Spring Boot | 3.2.5 | VMware | app-api.jar |
| **后端解析** | Spring Boot | 3.2.5 | VMware | app-worker.jar |
| **ORM** | Spring Data JPA + Hibernate | — | Red Hat | — |
| **数据库** | MySQL | 8.0 | Oracle | — |
| **文件解析** | Apache Tika 2.x + PDFBox 3.x + Flexmark + Tesseract | — | Apache | — |
| **认证** | JWT（Access + Refresh） + Spring Security | — | Spring | — |
| **Web 端（保留）** | Vue 3 + Vite + Naive UI + sql.js + IndexedDB | 现有 | — | 不重写 |

> **砍掉的项**：iOS 端 + iosMain + macOS 资源、Redis 依赖、Web 端 KMP Wasm 重写、复杂双向同步、5 套同步元数据字段、自写 AES 加密。

### 2.3 端侧 AI 推理的取舍

**不实现端侧推理**。所有 AI 调用走云端 API（DeepSeek 为主），由后端代理 + 限流。`AiProvider` 接口在 commonMain 中定义，v4 再加 `LocalAiProvider` 实现。

原因：

1. 包体积灾难：TFLite 引擎 + 模型文件 +20-50MB
2. 效果一致性：跨端结果有细微差异
3. 人才稀缺：能调通 TFLite 量化 + 模型转换的工程师少
4. 不是核心竞争力：LearnSpark 是学习管理工具，AI 是辅助

---

## 三、离线优先 + 冲突副本同步设计

### 3.1 同步策略：冲突副本（Conflict Copy）

**核心原则**：服务端是真理源（Source of Truth），但**不覆盖用户的离线修改**。

```
客户端（KMP）                              服务端（Spring Boot）
┌─────────────────────┐                  ┌─────────────────────┐
│  Compose UI 操作    │                  │  REST API            │
│      ↓              │                  │      ↓               │
│  SQLDelight 本地DB  │ ── 单向上传 ──→  │  校验 baseVersion    │
│  (即时读写)         │                  │      ↓               │
│      ↓              │  409 冲突         │  不匹配 → 409 + 最新  │
│  检测冲突           │ ←───────────────  │  匹配 → 写入 + version+1│
│      ↓              │                  │                      │
│  ┌───┴────┐         │                  │  MySQL 主数据         │
│  │ 保留   │         │                  │                      │
│  │ 本地   │         │                  │                      │
│  │ 原版   │         │                  │                      │
│  └───┬────┘         │                  │                      │
│      ↓              │                  │                      │
│  复制为冲突副本      │                  │                      │
│  (xxx 冲突副本)      │                  │                      │
│  is_dirty=1         │ ── 重新上传 ──→  │  接受两个版本          │
│      ↓              │                  │                      │
│  更新本地为服务端版  │ ←── Pull 拉取 ── │                      │
│  通知用户           │  (按 updated_at) │                      │
└─────────────────────┘                  └─────────────────────┘
```

**关键设计**：

- **单向上传**：客户端创建/修改/删除自己的数据 → POST /sync/upload
- **冲突副本**：当 baseVersion 不匹配时，本地数据复制为新记录「xxx (冲突副本 YYYY-MM-DD HH:mm)」
- **重新上传**：副本也标记 `is_dirty=1`，自动重试上传（生成新 ID）
- **Pull 拉取**：客户端启动 + 后台定时拉取 `updated_at > lastPullAt` 的全部数据

### 3.2 客户端同步流程

```kotlin
// commonMain
class SyncEngine(
    private val api: SyncApi,
    private val db: LearnSparkDatabase,
    private val clock: Clock,
) {
    /** 联网时由 UI 或后台 Worker 触发 */
    suspend fun syncOnce() {
        // 1. 推送本地脏数据
        val dirty = db.projectsQueries.selectDirty().executeAsList()
        if (dirty.isNotEmpty()) {
            val results = api.upload(dirty.map { it.toChange() })
            results.forEach { r ->
                when (r.status) {
                    "ok" -> db.projectsQueries.markSynced(r.id, r.serverVersion)
                    "conflict" -> handleConflict(r)  // 关键修正
                }
            }
        }
        // 2. 拉取服务端变更
        val since = lastPullAt()
        var cursor = since
        while (true) {
            val page = api.pull(cursor, limit = 200)
            page.records.forEach { r -> applyRemote(r) }
            cursor = page.latestUpdatedAt
            if (!page.hasMore) break
        }
        saveLastPullAt(cursor)
    }

    private suspend fun handleConflict(result: ConflictResult) {
        // 保留本地为副本，再接受服务端版本
        val localData = db.projectsQueries.selectById(result.id).executeAsOne()
        val conflictCopy = localData.copy(
            id = UUID.randomUUID().toString(),
            name = "${localData.name} (冲突副本 ${clock.now().toFormatted()})",
            isDirty = true,
            localUpdatedAt = clock.now().millis,
            serverVersion = 0,
        )
        db.projectsQueries.insert(conflictCopy)  // 保存冲突副本（等待上传）
        db.projectsQueries.markSynced(result.id, result.serverVersion)  // 本地接受服务端最新
        notifyUser("检测到冲突，已为您保留本地版本为副本")
    }

    private suspend fun applyRemote(remote: RemoteRecord) {
        when (remote.table) {
            "projects" -> db.projectsQueries.upsertFromServer(remote.payload)
            "phases" -> db.phasesQueries.upsertFromServer(remote.payload)
            // ...
        }
    }
}
```

### 3.3 本地数据库（SQLDelight）

#### 3.3.1 projects.sq（最小化字段）

```sql
-- commonMain/sqldelight/com/learnspark/db/projects.sq
CREATE TABLE projects (
    id              TEXT NOT NULL PRIMARY KEY,
    user_id         TEXT NOT NULL,
    name            TEXT NOT NULL,
    description     TEXT,
    goal            TEXT,
    daily_hours     INTEGER NOT NULL DEFAULT 2,
    is_ai_generated INTEGER AS Boolean DEFAULT 0,
    status          TEXT NOT NULL DEFAULT 'active',
    cover_color     TEXT NOT NULL DEFAULT '#18a058',
    deleted_at      TEXT,

    -- 同步元数据（仅 4 个字段）
    local_updated_at INTEGER NOT NULL,
    server_version   INTEGER NOT NULL DEFAULT 0,
    is_dirty         INTEGER AS Boolean DEFAULT 0,
    is_conflict      INTEGER AS Boolean DEFAULT 0,

    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL
);

selectActiveByUser:
SELECT * FROM projects
WHERE user_id = :userId AND deleted_at IS NULL
ORDER BY created_at DESC;

selectById:
SELECT * FROM projects WHERE id = :id;

selectDirty:
SELECT * FROM projects WHERE is_dirty = 1 ORDER BY local_updated_at ASC;

insert:
INSERT INTO projects(
    id, user_id, name, description, goal, daily_hours, is_ai_generated,
    status, cover_color, deleted_at, local_updated_at, server_version,
    is_dirty, created_at, updated_at
) VALUES (
    :id, :userId, :name, :description, :goal, :dailyHours, :isAiGenerated,
    :status, :coverColor, :deletedAt, :localUpdatedAt, :serverVersion,
    :isDirty, :createdAt, :updatedAt
);

update:
UPDATE projects SET
    name = :name, description = :description, goal = :goal,
    daily_hours = :dailyHours, is_ai_generated = :isAiGenerated,
    status = :status, cover_color = :coverColor, deleted_at = :deletedAt,
    local_updated_at = :localUpdatedAt, is_dirty = :isDirty, updated_at = :updatedAt
WHERE id = :id;

markSynced:
UPDATE projects SET is_dirty = 0, server_version = :version WHERE id = :id;
```

> 同步字段保持 3 个（含 `is_dirty`）+ 1 个 `is_conflict`——这是冲突副本策略的最小成本。

#### 3.3.2 平台 SqlDriver（expect/actual，双端）

```kotlin
// commonMain
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

// androidMain
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(LearnSparkDatabase.Schema, context, "learnspark.db")
}

// desktopMain (JVM)
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        JdbcSqliteDriver("jdbc:sqlite:${System.getProperty("user.home")}/.learnspark/learnspark.db").also {
            if (!File("${System.getProperty("user.home")}/.learnspark/learnspark.db").exists()) {
                LearnSparkDatabase.Schema.create(it)
            }
        }
}
```

> expect/actual 仅 Android + Desktop 两端，无 iosMain、无 wasmJsMain。

#### 3.3.3 迁移策略

SQLDelight 自带 `.sqm` 迁移文件机制。仅设计 2 个版本：

- `1.sqm` 初始 schema
- `2.sqm` 增加 `is_dirty` 字段（如从 v1 schema 演进）

### 3.4 服务端同步端点

#### 3.4.1 单向上传（POST /api/v1/sync/upload）

```http
POST /api/v1/sync/upload
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "changes": [
    {
      "table": "projects",
      "operation": "upsert",
      "id": "uuid",
      "payload": { ... },
      "baseVersion": 3
    }
  ]
}
```

**响应**：

```json
{
  "results": [
    {
      "id": "uuid",
      "status": "ok",          // ok | conflict | forbidden
      "serverVersion": 4,
      "latest": { ... }        // 冲突时返回服务端最新记录
    }
  ]
}
```

**服务端逻辑**：
- 校验 `user_id` 归属当前 token
- `baseVersion` 与服务端 `version` 不匹配 → 返回 `conflict` + 最新数据
- 写成功后 `version + 1`，更新 `updated_at`

#### 3.4.2 Pull 拉取（GET /api/v1/sync/pull）

```http
GET /api/v1/sync/pull?since=2026-01-15T10:00:00Z&limit=200
Authorization: Bearer <accessToken>
```

**响应**：

```json
{
  "records": [
    {
      "table": "projects",
      "operation": "upsert",
      "id": "uuid",
      "payload": { ... },
      "version": 5,
      "updated_at": "2026-01-15T10:05:00Z"
    }
  ],
  "latestUpdatedAt": "2026-01-15T10:05:00Z",
  "hasMore": false
}
```

**分页策略**：`since` 客户端传上次拉取的最大 `updated_at`，服务端按用户隔离返回该时间之后所有变更。`limit=200` 分页，`hasMore=true` 时客户端继续拉取。

### 3.5 冲突副本 UX 流程：把"技术冲突"翻译成"用户语言"

技术层面"冲突副本"已经解决了数据丢失问题，但如果不设计好 UX，普通用户看到"我的笔记 (冲突副本 2026-07-21)"的第一反应会是"软件出 Bug 了"，而不是"哦，我刚才离线编辑的内容被保留了"。明确 UX 流程：

#### 3.5.1 即时通知（冲突发生瞬间）

冲突发生时，**客户端**和**UI**都要给出明确反馈：

```kotlin
// SyncEngine.handleConflict() 增强
private suspend fun handleConflict(result: ConflictResult) {
    val localData = db.projectsQueries.selectById(result.id).executeAsOne()
    val conflictCopy = localData.copy(
        id = UUID.randomUUID().toString(),
        name = "${localData.name} (冲突副本 ${clock.now().toFormatted()})",
        isConflict = true,                  // 新增冲突标记
        isDirty = true,
        localUpdatedAt = clock.now().millis,
        serverVersion = 0,
    )
    db.projectsQueries.insert(conflictCopy)
    db.projectsQueries.markSynced(result.id, result.serverVersion)

    // 1. 弹 Snackbar（用户当下能看见）
    snackbarHost.showSnackbar(
        message = "检测到版本冲突，您的修改已保存为副本",
        actionLabel = "查看",
        duration = SnackbarDuration.Long,
    )
    // 2. 同时写入应用内"通知中心"（用户事后能查到）
    notificationCenter.add(Notification(
        type = NotificationType.ConflictDetected,
        title = "数据冲突已自动处理",
        body = "「${localData.name}」的离线修改已保存为副本",
        actionUrl = "/projects/${result.id}?conflict=${conflictCopy.id}",
        createdAt = clock.now(),
    ))
}
```

#### 3.5.2 视觉区分（列表中一眼可辨）

冲突副本在列表中用**专属图标 + 颜色**标记，与普通记录区分：

| 元素 | 普通记录 | 冲突副本 |
|------|---------|---------|
| 列表项图标 | 默认文件夹/笔记图标 | 黄色三角警示图标 + 角标 "!" |
| 名称颜色 | 文本主色（深色） | 文本**琥珀色**（如 #F59E0B） |
| 名称后缀 | 无 | `(冲突副本 2026-07-21 14:30)` |
| 列表项右侧 | 无标记 | 「待处理」徽章 |
| 排序优先级 | 正常 | **置顶**（不与正常记录混在一起） |

```kotlin
// 列表项渲染（Compose）
@Composable
fun ProjectListItem(project: Project) {
    val isConflict = project.isConflict
    Row(
        verticalAlignment = Alignment.CenterVertically,
        backgroundColor = if (isConflict) MaterialTheme.colors.amber.copy(alpha = 0.05f) else Color.Transparent,
    ) {
        Icon(
            imageVector = if (isConflict) Icons.Warning else Icons.Folder,
            tint = if (isConflict) Color(0xFFF59E0B) else MaterialTheme.colors.primary,
            contentDescription = null,
        )
        Text(
            text = project.name,
            color = if (isConflict) Color(0xFFF59E0B) else MaterialTheme.colors.onSurface,
            fontWeight = if (isConflict) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (isConflict) {
            Spacer(Modifier.weight(1f))
            Badge { Text("待处理") }
        }
    }
}
```

#### 3.5.3 三个操作选项（用户决策权）

用户点击冲突副本后，进入**冲突详情页**，提供三个明确操作：

```
┌────────────────────────────────────────────────────┐
│  ← 返回   处理冲突                                  │
├────────────────────────────────────────────────────┤
│  您的修改（冲突副本）              服务端版本        │
│  ──────────────────────        ─────────────────  │
│  创建于 2026-07-21 14:25       更新于 2026-07-21  │
│                                 14:32 by iPhone    │
│                                                     │
│  内容预览：                      内容预览：          │
│  ┌─────────────────┐          ┌─────────────────┐ │
│  │ 离线编辑的版本   │          │ 服务端最新版本   │ │
│  │ ...             │          │ ...             │ │
│  └─────────────────┘          └─────────────────┘ │
│                                                     │
│  ┌──────────────────────────────────────────────┐ │
│  │  合并两个版本（打开对比编辑器）              │ │
│  └──────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────┐ │
│  │  保留此版本（用副本覆盖服务端）              │ │
│  └──────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────┐ │
│  │  删除副本（确认服务端版本是最终版）          │ │
│  └──────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────┘
```

**三个操作的实现**：

| 操作 | 后端调用 | 客户端行为 | 适用场景 |
|------|---------|----------|---------|
| **合并** | 拉取两个版本 + 打开 diff 视图 | 用户手动选择保留哪部分 | 两个版本都有价值 |
| **保留此版本** | `PUT /sync/upload` 用副本覆盖 | 副本成为正式版本，删除标记 | 用户的离线修改更重要 |
| **删除副本** | `DELETE /api/v1/projects/{conflictId}` | 副本消失，只保留服务端版本 | 服务端版本是用户想要的 |

**"合并"操作的实现细节**：

不做字符级 diff（CRDT 是 v4 议题），但提供一个**简化版合并**：

```kotlin
// 冲突合并对话框（Compose）
@Composable
fun ConflictMergeDialog(
    conflictCopy: Project,
    serverVersion: Project,
    onMerge: (merged: Project) -> Unit,
) {
    var userChoice by remember { mutableStateOf<FieldChoice>(FieldChoice.KeepServer) }

    AlertDialog(
        title = { Text("选择保留哪一版本的内容") },
        text = {
            Column {
                // 字段级别二选一
                FieldComparisonRow("名称", conflictCopy.name, serverVersion.name) {
                    userChoice = userChoice.copy(name = if (it) "copy" else "server")
                }
                FieldComparisonRow("描述", conflictCopy.description, serverVersion.description) {
                    userChoice = userChoice.copy(description = if (it) "copy" else "server")
                }
                // ... 其他字段
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val merged = Project(
                    name = if (userChoice.name == "copy") conflictCopy.name else serverVersion.name,
                    description = if (userChoice.description == "copy") conflictCopy.description else serverVersion.description,
                    // ...
                )
                onMerge(merged)
            }) { Text("确认合并") }
        },
    )
}
```

> 字段级别的"二选一"是简化版合并——不做字符级 diff（CRDT 是 v4 议题）。对于大多数冲突场景（项目名、描述、状态），字段级已足够。

#### 3.5.4 副本生命周期管理

- **生成**：冲突发生立即生成副本，标记 `isConflict=true`
- **待处理**：副本在用户处理前一直存在（不自动清理）
- **处理完成**（合并 / 保留 / 删除）：清除 `isConflict` 标记
- **自动清理**：超过 30 天未处理的"已合并"副本（用户已选保留某版本）自动删除，避免列表膨胀
- **手动清理**：设置页提供「清理所有冲突副本」按钮

#### 3.5.5 错误场景与提示

| 场景 | 用户提示 |
|------|---------|
| 副本生成成功 | Snackbar「已为您保留本地版本为副本」（黄色） |
| 副本上传成功 | Snackbar「冲突副本已同步到服务端」（绿色） |
| 副本上传失败 | Snackbar「副本同步失败，将在下次联网时重试」（红色） |
| 副本超过 30 天未处理 | 通知中心「您的冲突副本超过 30 天未处理，是否清理？」 |
| 用户误操作删除副本 | 撤销 SnackBar「副本已删除（5 秒内可撤销）」 |

---

## 四、安全架构

### 4.1 双令牌 + 设备绑定

**不引入 Redis**——黑名单与会话改用 MySQL 表。

#### 4.1.1 令牌策略

| 令牌 | 有效期 | 存储位置 | 用途 |
|------|--------|---------|------|
| AccessToken | 1 小时 | 内存（不落盘） | API 请求认证 |
| RefreshToken | 30 天 | 平台安全存储 | 刷新 AccessToken |
| deviceId | 永久 | 平台安全存储 | 设备标识 |

#### 4.1.2 devices 表

```sql
CREATE TABLE devices (
    id                  VARCHAR(36) PRIMARY KEY,
    user_id             VARCHAR(36) NOT NULL,
    device_name         VARCHAR(100),
    device_type         VARCHAR(20) NOT NULL,    -- android / desktop
    device_fingerprint VARCHAR(255),
    refresh_token_hash  VARCHAR(255),
    last_active_at      DATETIME(3),
    created_at          DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_devices_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE revoked_tokens (
    id          VARCHAR(36) PRIMARY KEY,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    user_id     VARCHAR(36) NOT NULL,
    revoked_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_revoked_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 4.1.3 API 端点

```
POST /api/v1/auth/login             → 登录，返回 accessToken + refreshToken + deviceId
POST /api/v1/auth/refresh           → 刷新 accessToken
POST /api/v1/auth/logout            → 撤销 refreshToken + 当前设备会话
GET  /api/v1/auth/devices           → 查看已登录设备
DELETE /api/v1/auth/devices/{id}    → 远程注销设备
```

### 4.2 平台安全存储（expect/actual，双端）

```kotlin
// commonMain
interface SecureStorage {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}

expect fun createSecureStorage(): SecureStorage
```

#### 4.2.1 Android — Keystore + EncryptedSharedPreferences

```kotlin
actual fun createSecureStorage(): SecureStorage = AndroidKeystoreStorage(context)

class AndroidKeystoreStorage(private val ctx: Context) : SecureStorage {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx, "learnspark_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}
```

#### 4.2.2 Desktop — JNA 调用系统 API

**关键决策**：用 JNA 调用操作系统的"当前用户凭据加密"接口。这是桌面端开发的标准做法（VS Code、IDEA、Postman 都这么做）。不采用自写 AES 加密——密钥管理本身就是坑。

**依赖**：

```kotlin
// build.gradle.kts
implementation("net.java.dev.jna:jna:5.13.0")
implementation("net.java.dev.jna:jna-platform:5.13.0")
```

**实现思路**：

```kotlin
actual fun createSecureStorage(): SecureStorage = DesktopSystemStorage()

class DesktopSystemStorage : SecureStorage {
    override fun put(key: String, value: String) {
        when (Platform.getOSType()) {
            Platform.WINDOWS -> {
                // 调用 crypt32.dll 的 CryptProtectData
                // 加密后只能用同一 Windows 用户同一机器解密
                WindowsDpapi.protect(value.toByteArray(Charsets.UTF_8))
                    .let { writeToFile(".learnspark/$key.dpapi", it) }
            }
            Platform.MAC -> {
                // 调用 Security.framework 的 SecKeychainAddGenericPassword
                MacKeychain.set(key, value)
            }
            Platform.LINUX -> {
                // 通过 libsecret / D-Bus 写入 GNOME Keyring 或 KWallet
                // 没有 Keyring 时降级到加密文件 + 警告用户
                LinuxSecretStore.set(key, value)
                    ?: fallbackEncryptedFile(key, value)
            }
        }
    }

    override fun get(key: String): String? {
        return when (Platform.getOSType()) {
            Platform.WINDOWS -> {
                WindowsDpapi.unprotect(readFile(".learnspark/$key.dpapi"))
                    .toString(Charsets.UTF_8)
            }
            Platform.MAC -> MacKeychain.get(key)
            Platform.LINUX -> LinuxSecretStore.get(key)
        }
    }

    override fun remove(key: String) {
        when (Platform.getOSType()) {
            Platform.WINDOWS -> deleteFile(".learnspark/$key.dpapi")
            Platform.MAC -> MacKeychain.remove(key)
            Platform.LINUX -> LinuxSecretStore.remove(key)
        }
    }
}
```

**平台映射**：

| 操作系统 | 系统 API | 备注 |
|---------|---------|------|
| Windows | `CryptProtectData` / `CryptUnprotectData` (DPAPI) | 绑定 Windows 用户账户，跨进程可用 |
| macOS | Keychain Services（`SecKeychainAddGenericPassword`） | 已在生产广泛使用 |
| Linux | libsecret（GNOME Keyring / KWallet） | 通过 D-Bus 访问 |

### 4.3 Linux 降级方案：加密文件 + 主密码

Linux 桌面环境碎片化严重，必须为 JNA 调用失败场景准备降级方案：

| 环境 | libsecret 支持 | 降级方案 |
|------|---------------|---------|
| Ubuntu GNOME | 是 | 优先用 Keyring |
| Ubuntu KUbuntu (KDE) | 是（KWallet） | 优先用 KWallet |
| Ubuntu Server（无 GUI） | 否 | 加密文件 + 主密码 |
| CentOS Stream 桌面 | 是 | 优先用 Keyring |
| Docker 容器 | 否 | 加密文件 + 主密码 |
| WSL2（无 systemd） | 否 | 加密文件 + 主密码 |

**降级模式实现**：

```kotlin
// DesktopSystemStorage.kt
class DesktopSystemStorage : SecureStorage {
    private val key: SecretKey by lazy {
        // 尝试调用系统 API（首选）
        when (Platform.getOSType()) {
            Platform.WINDOWS -> WindowsDpapi.deriveKey()
            Platform.MAC -> MacKeychain.deriveKey()
            Platform.LINUX -> LinuxSecretStore.deriveKey()
                ?: run {
                    // 降级：加密文件 + 主密码
                    log.warn("系统级凭据存储不可用，降级到主密码模式")
                    notifyUser("当前环境不支持系统级凭据存储，已启用主密码保护模式")
                    MasterPasswordManager.deriveKey()
                }
        }
    }

    override fun put(key: String, value: String) {
        val encrypted = encrypt(value.toByteArray(Charsets.UTF_8), this.key)
        writeToFile(".learnspark/$key.enc", encrypted)
    }
}

// MasterPasswordManager.kt —— 主密码派生密钥
object MasterPasswordManager {
    private const val PBKDF2_ITERATIONS = 600_000  // OWASP 2023 推荐
    private const val KEY_LENGTH = 256  // bits

    fun deriveKey(): SecretKey {
        // 1. 检查是否已设置主密码
        val saltFile = File(System.getProperty("user.home"), ".learnspark/master.salt")
        val masterHashFile = File(System.getProperty("user.home"), ".learnspark/master.hash")

        if (!saltFile.exists() || !masterHashFile.exists()) {
            // 首次启动：UI 提示用户设置主密码
            val masterPassword = promptUserForMasterPassword(isFirstTime = true)
            val salt = SecureRandom().generateSeed(16)
            saltFile.writeBytes(salt)
            masterHashFile.writeBytes(hashMasterPassword(masterPassword, salt))
        }

        // 2. 每次启动：UI 提示用户输入主密码
        val masterPassword = promptUserForMasterPassword(isFirstTime = false)
        verifyMasterPassword(masterPassword, masterHashFile.readBytes(), saltFile.readBytes())

        // 3. PBKDF2 派生 256-bit AES key
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(masterPassword.toCharArray(), saltFile.readBytes(), PBKDF2_ITERATIONS, KEY_LENGTH)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun promptUserForMasterPassword(isFirstTime: Boolean): String {
        // 弹 Compose 对话框（参考 §4.5 Desktop BiometricAuth）
        // 首次：要求设置 + 确认密码
        // 后续：要求输入密码
    }
}
```

**UI 提示规范**：

- 启动时如果检测到降级模式，**主界面顶部显示黄色提示条**：「当前环境不支持系统级凭据存储，已启用主密码保护模式。设置 → 安全 可修改主密码」
- 设置页提供「修改主密码」「重置主密码（需验证旧密码）」
- 提示用户**主密码不可找回**（与系统 Keyring 最大的差异）
- 加密算法：AES-256-GCM，PBKDF2 600k 次迭代（OWASP 2023 推荐）

**生产验证清单**：

阶段一必须跑通 6 种环境，**所有环境都能正常启动 + 加解密**：

- [ ] Windows 10/11（DPAPI 路径）
- [ ] macOS（Keychain 路径）
- [ ] Ubuntu 22.04 GNOME（libsecret 路径）
- [ ] **Ubuntu 22.04 Server 无 GUI**（降级到主密码）
- [ ] **Docker 容器**（降级到主密码）
- [ ] **WSL2**（降级到主密码）

### 4.4 Ktor Auth 插件 + RefreshToken 自动刷新

```kotlin
// commonMain
fun createHttpClient(secureStorage: SecureStorage): HttpClient = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(Auth) {
        bearer {
            loadTokens {
                val access = secureStorage.get("access_token")
                val refresh = secureStorage.get("refresh_token")
                if (access != null && refresh != null) {
                    BearerTokens(access, refresh)
                } else null
            }
            refreshTokens {
                val refresh = oldTokens?.refreshToken ?: return@refreshTokens null
                val response = client.post("${baseUrl}/auth/refresh") {
                    setBody(mapOf("refreshToken" to refresh))
                }
                if (response.status == HttpStatusCode.OK) {
                    val body = response.body<RefreshResponse>()
                    secureStorage.put("access_token", body.accessToken)
                    secureStorage.put("refresh_token", body.refreshToken)
                    BearerTokens(body.accessToken, body.refreshToken)
                } else {
                    secureStorage.remove("access_token")
                    secureStorage.remove("refresh_token")
                    null
                }
            }
            sendWithoutRequest { req -> req.url.encodedPath.contains("/auth/") }
        }
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
    }
}
```

### 4.5 生物识别（expect/actual，双端）

```kotlin
// commonMain
interface BiometricAuth {
    suspend fun isAvailable(): Boolean
    suspend fun authenticate(reason: String): Boolean
}
expect class BiometricAuthFactory() {
    fun create(): BiometricAuth
}
```

- **Android**：`androidx.biometric.BiometricPrompt`
- **Desktop**：Compose 对话框弹 PIN 码（PIN 同样通过 JNA 加密存储）

### 4.6 HTTPS 证书锁定

简化为「生产环境开启证书锁定，开发环境关闭」：

- **Android**：OkHttp `CertificatePinner`
- **Desktop**：Ktor Java HttpClient `SSLContext` + 自定义 `TrustManager`

> 锁定指纹从环境变量 `API_CERT_SHA256` 注入。

### 4.7 安全检查清单

| 维度 | 措施 | 状态 |
|------|------|------|
| 传输安全 | HTTPS + 证书锁定 + HSTS | 保留 |
| 认证 | 双令牌 + 设备绑定 | 保留 |
| 生物识别 | 平台原生 | 保留（仅 Android / Desktop PIN） |
| 存储安全 | Keystore / JNA→DPAPI/Keychain/libsecret | 升级（不再自写 AES） |
| API 安全 | 速率限制 + 防 CSRF | 保留 |
| 代码安全 | R8 minifyEnabled=false | 保留（无 iOS，砍 iOS Strip） |
| 越狱/Root 检测 | 移动端检测 | v4 再做 |
| 密钥管理 | AI Key + RefreshToken 系统加密 | 保留 |
| 审计日志 | 登录/设备/敏感操作 | 保留 |

---

## 五、文件处理增强（双进程隔离架构）

### 5.1 为什么需要双进程

如果用 Spring `@Async` + `ThreadPoolTaskExecutor` 跑 Tika/OCR：

- **线程共享 JVM 堆内存**：OCR 大文件（50MB 图片）会把堆吃光，触发 Full GC，主服务请求全部卡顿
- **OOM 拖垮主服务**：单进程跑多个 `-Xmx` 不能物理隔离，一个组件崩全崩
- **重启影响所有用户**：解析挂了，用户连登录都做不了

### 5.2 方案：app-api + app-worker 双进程 + 数据库轮询

**不引入 Redis/RabbitMQ**——保持基础设施简单，通过 MySQL 状态机 + 定时轮询实现进程间协作。

**关键决策：选择「轮询」而非「监听」**

| 机制 | 实现方式 | 复杂度 | 决策 |
|------|---------|--------|------|
| 监听 | CDC（Debezium）+ 触发器 + 消息推送 | 高（引入新组件） | 不采用 |
| **轮询** | **app-worker 定时 SELECT** | **低（MySQL 原生）** | **采用** |

> "监听"在生产中通常需要引入 Debezium 等 CDC 工具，与"保持轻量"的初衷相悖。轮询虽然有 5 秒延迟，但解析任务本身就是分钟级，5 秒延迟完全可接受。

```
┌──────────────────────┐                          ┌──────────────────────┐
│  app-api.jar         │                          │  app-worker.jar      │
│  -Xmx512m             │                          │   -Xmx1024m           │
│                      │                          │                      │
│  POST /upload ──→    │   INSERT                 │   @Scheduled         │
│  (status=PENDING)    │ ─────────────────────→   │   (fixedDelay=5000)  │
│                      │                          │           ↓           │
│  立即返回「上传成功」 │                          │   SELECT WHERE        │
│  给客户端            │                          │     status=PENDING    │
│                      │                          │           ↓           │
│                      │                          │   UPDATE status=      │
│                      │ ←─────────────────────  │     PROCESSING        │
│                      │                          │           ↓           │
│                      │                          │   Tika / OCR 解析     │
│                      │                          │           ↓           │
│                      │ ←─────────────────────  │   UPDATE status=      │
│                      │   READY / FAILED          │     READY             │
│                      │                          │                      │
│  业务端口 8080        │                          │  内存爆炸不影响 API    │
└──────────────────────┘                          └──────────────────────┘
                  ↓                                            ↓
                  └──────────────┬─────────────────────────────┘
                                 ↓
                          MySQL（共享 DB）
```

### 5.3 启动脚本

```bash
# start.sh —— 同时启动两个进程
#!/bin/bash
set -e

# API 服务（限制 512MB，防止 OCR 影响）
java -Xmx512m -jar app-api.jar --spring.profiles.active=prod &
API_PID=$!

# 解析工作进程（限制 1GB，够 Tika + OCR 跑大文件）
java -Xmx1024m -jar app-worker.jar --spring.profiles.active=worker &
WORKER_PID=$!

trap "kill $API_PID $WORKER_PID" SIGINT SIGTERM
wait
```

### 5.4 app-worker 实现要点：定时轮询

**核心实现**：app-worker 是个独立的 Spring Boot 应用，启动后跑一个 `@Scheduled` 定时任务，**每 5 秒轮询一次**待处理任务。

```java
// app-worker 的核心逻辑（轮询而非监听）
@Component
public class ParseJobProcessor {

    @Scheduled(fixedDelay = 5000)  // 每 5 秒轮询一次
    @Transactional
    public void pollAndProcessPendingJobs() {
        // 1. 拉取 PENDING 任务（带悲观锁防止多 worker 抢同一任务）
        List<FileParseJob> pending = jobRepository.findPendingForUpdate(
            PageRequest.of(0, 3)  // 每次最多处理 3 个，避免单次占用过久
        );
        if (pending.isEmpty()) return;

        for (FileParseJob job : pending) {
            try {
                // 2. 标记为 PROCESSING（轮询→处理的状态转换）
                job.setStatus("PROCESSING");
                jobRepository.save(job);

                // 3. 路由到对应解析器
                ParseResult result = switch (job.getFileType()) {
                    case "pdf" -> pdfBoxParser.parse(job.getFilePath());
                    case "docx", "xlsx", "pptx" -> tikaParser.parse(job.getFilePath());
                    case "png", "jpg" -> tesseractOcr.parse(job.getFilePath());
                    default -> flexmarkParser.parse(job.getFilePath());
                };

                // 4. 写回解析内容
                knowledgeService.updateContent(job.getEntryId(), result);
                job.setStatus("READY");
                job.setCompletedAt(Instant.now());
            } catch (Exception e) {
                log.error("Parse failed for job {}", job.getId(), e);
                job.setStatus("FAILED");
                job.setErrorMessage(e.getMessage());
            } finally {
                jobRepository.save(job);
            }
        }
    }
}
```

**轮询机制的关键设计**：

| 设计点 | 决策 | 理由 |
|--------|------|------|
| 轮询间隔 | **5 秒** | 解析任务普遍 10s~60s，5s 延迟可接受；过短会增加 DB 压力 |
| 单次拉取数量 | **3 个** | 避免 worker 单次占用过久导致下一个轮询周期延迟 |
| 并发安全 | **`SELECT ... FOR UPDATE`** | 防止多 worker 实例（未来水平扩展）抢同一任务 |
| 任务状态机 | `PENDING → PROCESSING → READY/FAILED` | 状态清晰，便于排查与重试 |
| worker 启动行为 | **不预热** | 首次解析时才加载 Tika/OCR 引擎，worker 启动快 |
| 失败重试 | **状态为 FAILED，**人工/定时任务重置为 PENDING | 不做自动重试，避免失败任务无限循环 |

**为什么"轮询"足够用**：

- 解析任务本身是**分钟级**（PDF 大文件解析可能要 30 秒~2 分钟）
- 5 秒轮询延迟相对于分钟级任务，**完全可接受**
- 实现极简：纯 SQL + `@Scheduled`，无需任何新组件
- 调试容易：`SELECT * FROM file_parse_jobs WHERE status='PENDING'` 即可看到所有待处理任务

**进程隔离的优势**：

| 故障场景 | 影响 |
|---------|------|
| OCR 大文件 OOM | worker 进程挂，API 仍正常 |
| Tika 解析死循环 | worker 僵死，下一轮跳过 |
| DB 短时不可用 | 轮询抛异常，下一次重试 |
| 内存泄漏 | 监控报警后单独重启 |

### 5.5 文件大小限制

- 同步解析（< 1MB）：app-api 直接处理（小文件 Tika 启动成本低）
- 异步解析（1MB ~ 50MB）：app-api 写 `status=PENDING` 后立即返回，worker 轮询处理
- 超过 50MB：拒绝上传，提示用户分割

### 5.6 Docker Compose 配置

```yaml
# docker-compose.yml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    # ...

  app-api:
    image: learnspark/api:latest
    deploy:
      resources:
        limits:
          memory: 512M
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - mysql

  app-worker:
    image: learnspark/api:latest  # 同一镜像，不同 profile
    command: ["java", "-Xmx1024m", "-jar", "app.jar", "--spring.profiles.active=worker"]
    deploy:
      resources:
        limits:
          memory: 1280M   # 留 256M buffer
    depends_on:
      - mysql
```

> **关键**：两个服务用同一镜像，通过 `spring.profiles.active=worker` 激活 worker 模式，避免双镜像维护成本。

### 5.7 OCR 服务

Tesseract 在 JVM 上跑成本高（首次加载慢、占内存）。方案：

- **当前阶段**：OCR 跑在 app-worker 内，限制单文件 50MB
- **v4.0 阶段**：如有大量 OCR 需求，迁到独立 OCR 微服务（Python + Tesseract / PaddleOCR）

---

## 六、AI 模块

### 6.1 抽象接口（commonMain）

```kotlin
// commonMain
package com.learnspark.ai

interface AiProvider {
    val name: String
    suspend fun chatCompletion(prompt: String, opts: ChatOpts = ChatOpts()): String
    suspend fun review(content: String): ReviewResult
    suspend fun testConnection(): Boolean
}

data class ChatOpts(
    val model: String? = null,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000,
)

data class ReviewResult(
    val score: Int,
    val feedback: String,
    val highlights: List<String>,
)

class AiRouter(
    private val provider: AiProvider,
) {
    suspend fun generatePlan(prompt: String, online: Boolean): String {
        if (!online) {
            return fallbackTemplatePlan(prompt)  // 模板兜底
        }
        return provider.chatCompletion(prompt)
    }
}
```

> **不实现端侧推理**，`AiProvider` 只接云端 API。

### 6.2 服务端 AI 代理

所有 AI 调用走**后端代理**而非客户端直连：

```
客户端 → POST /api/v1/ai/chat → app-api.jar AiController → DeepSeekClient
                                                          ↓
                                                    重试 / 限流 / 日志
```

**理由**：

1. **API Key 不落客户端**：避免泄露 + 便于轮换
2. **统一限流**：DeepSeek 有 QPS 限制
3. **可观测性**：所有 AI 请求可被审计与计费
4. **可降级**：服务端可灵活切换 Provider

### 6.3 AI Key 管理

- 服务端 `.env` 存 `DEEPSEEK_API_KEY`（生产用 K8s Secret）
- 客户端 AI Key（旧版允许用户自填）改为**服务端全局配置**——简化

---

## 七、模块拆分与任务清单（14 周）

### 阶段零：基础设施与 PoC（2 周）

> **重要**：阶段零只做技术验证，不交付产品功能。所有 PoC 产出物是「报告 + 可运行的 Demo」。

#### 0.1 KMP 双端脚手架 PoC（1 周）
- KMP Wizard 跑通 Desktop + Android 双端「Hello World」
- 验证：Gradle 配置、Android 互操作、Desktop JVM 打包
- 产出：双端跑通截图 + 真实构建时间 + 遇到的所有坑

#### 0.2 SQLDelight + Ktor 跨端 PoC（0.5 周）
- 双端实现「本地写一条记录 → Pull 拉取服务端 → 显示」的最小流程
- 产出：PoC Demo + 报告（每个平台的真实启动时间）

#### 0.3 JNA 系统 API + 降级方案 PoC（0.5 周）
- 验证 Windows DPAPI / macOS Keychain / Linux libsecret 在 JNA 下调用成功
- 测 4 种环境：Windows 10/11、macOS、Ubuntu GNOME、Ubuntu Server（无 GUI）
- **降级方案验证**：在 Ubuntu Server / Docker 容器 / WSL2 三个无 GUI 环境，确认主密码派生 + 加密文件路径可用
- 产出：JNA 兼容性矩阵 + 降级方案确认报告

### 阶段一：核心业务与同步（5 周）

#### 1.1 KMP 项目正式化 + JNA 安全存储（1 周）
- 项目结构（`composeApp` + `server`）
- 主题系统、底部导航、响应式布局
- Koin DI、Voyager 导航、Kermit 日志
- expect/actual 骨架：SecureStorage（JNA 实现）/ BiometricAuth / DatabaseDriverFactory
- CI/CD：GitHub Actions Ubuntu runner

#### 1.2 后端安全增强（1 周）
- Flyway V2：新增 devices + revoked_tokens 表
- RefreshToken 服务
- 设备管理 Service + Controller
- SecurityConfig 双令牌过滤器

#### 1.3 冲突副本同步引擎（含 UX 流程）（1 周）
- `SyncController`（`/sync/upload` + `/sync/pull`）
- `SyncService`（乐观锁 + version 校验）
- 客户端 `SyncEngine`（冲突副本逻辑 + 失败重试队列）
- **冲突副本 UX 完整实现**：
  - 冲突发生时 Snackbar + 通知中心双通道
  - 列表视觉区分（琥珀色图标 + "待处理"徽章 + 置顶）
  - 冲突详情页：合并 / 保留 / 删除三个操作按钮
  - 简化版字段级合并对话框
  - 副本生命周期管理（30 天自动清理）
- 同步状态 UI（上次同步时间、待同步数量、网络状态指示）

#### 1.4 学习项目模块（1 周）
- 项目列表（卡片网格 + 离线缓存）
- 项目详情（阶段时间轴 + 任务卡片）
- 项目 / 阶段 / 任务 CRUD

#### 1.5 任务提交与审核（0.5 周）
- 任务提交页（Markdown 编辑器）
- AI 审核（云端 DeepSeek）
- 审核结果展示

#### 1.6 Desktop 端交互适配（0.5 周）
- 鼠标右键菜单
- 键盘快捷键（Ctrl+S 保存、Ctrl+N 新建）
- 多窗口 / 标签页

### 阶段二：后端双进程 + 文件解析（4 周）

#### 2.1 后端进程拆分：app-api + app-worker（1 周）
- 拆分 `app-api.jar`（业务）+ `app-worker.jar`（解析）
- 共享同一镜像，通过 `spring.profiles.active=worker` 切换
- Docker Compose 配置内存限制（API 512M / worker 1G）
- 启动脚本 + 健康检查
- **app-worker 实现定时轮询**（@Scheduled fixedDelay=5000ms）
- **`SELECT ... FOR UPDATE` 悲观锁**防止多 worker 抢任务
- **MySQL 状态机**：`PENDING → PROCESSING → READY/FAILED`
- **故障测试**：手动 kill worker 进程 → 重启后继续处理积压任务

#### 2.2 Tika + PDFBox 集成（1 周）
- 同步解析（< 1MB）
- 异步解析（1MB ~ 50MB）
- file_parse_jobs 状态机
- 解析完成 WebSocket 推送

#### 2.3 Tesseract OCR 集成（1 周）
- 图片 OCR 在 app-worker 中处理
- 单文件 50MB 上限
- 失败重试 3 次

#### 2.4 知识库模块（1 周）
- 知识库列表 + 标签过滤
- 知识详情（Compose Markdown + 双向链接）
- 文件上传进度条（Desktop / Android）
- 全文搜索（MySQL FULLTEXT + ngram）

### 阶段三：打磨与发布（3 周）

#### 3.1 数据迁移（0.5 周）
- 旧版 Vue3 数据导出脚本（前端导出 JSON）
- 服务端导入脚本（JSON → MySQL）
- 旧版数据兜底：客户端首次启动时检测本地缓存

#### 3.2 后端部署（0.5 周）
- Docker Compose 完善（双服务）
- HTTPS 配置 + 证书生成
- 数据备份脚本

#### 3.3 体验打磨（1 周）
- 成就系统（积分 + 连续打卡 + 徽章）
- 仪表盘（今日待完成 + 7 日热力图 + AI 每日一句）
- 通知系统（Android FCM + Desktop AWT SystemTray + 邮件）
- 主题与动画（浅色/深色 + 切换动画 + 骨架屏）

#### 3.4 双端发布（1 周）
- Desktop 端：制作安装包（MSI/DMG/DEB）+ 系统托盘图标
- Android 端：签名 + AAB 打包 + 深色模式适配
- Web 端（Vue3）：添加「下载桌面版」引导 + 移动端 H5 优化
- GitHub Actions Ubuntu runner 自动化打包

### 阶段四：稳定性验证（1 周，可选缓冲）

> 这是缓冲周，专门用于压测、bug 修复、文档补全。如果前 13 周顺利，可用于：
- 自动化测试覆盖率补全
- 内部用户 1 周灰度试用
- 文档与培训材料

---

## 八、数据库变更（Flyway 增量迁移）

### V2__add_security_tables.sql

```sql
CREATE TABLE devices (
    id                  VARCHAR(36) PRIMARY KEY,
    user_id             VARCHAR(36) NOT NULL,
    device_name         VARCHAR(100),
    device_type         VARCHAR(20) NOT NULL,
    device_fingerprint  VARCHAR(255),
    refresh_token_hash  VARCHAR(255),
    last_active_at      DATETIME(3),
    created_at          DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_devices_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE revoked_tokens (
    id          VARCHAR(36) PRIMARY KEY,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    user_id     VARCHAR(36) NOT NULL,
    revoked_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_revoked_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### V3__add_sync_version_columns.sql

```sql
ALTER TABLE projects         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE phases           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tasks            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE submissions      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_entries ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_links  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE reminder_settings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_scores      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_badges      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

### V4__add_file_parse_tables.sql

```sql
CREATE TABLE file_parse_jobs (
    id              VARCHAR(36) PRIMARY KEY,
    entry_id        VARCHAR(36) NOT NULL,
    file_path       TEXT NOT NULL,
    file_type       VARCHAR(20) NOT NULL,
    status          VARCHAR(20) DEFAULT 'pending',  -- pending / processing / ready / failed
    error_message   TEXT,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    completed_at    DATETIME(3),
    INDEX idx_parse_status (status),
    CONSTRAINT fk_parse_entry FOREIGN KEY (entry_id) REFERENCES knowledge_entries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 九、KMP 项目结构

```
learnspark-kmp/
├── build.gradle.kts                     # 根构建
├── settings.gradle.kts
├── gradle/libs.versions.toml
│
├── composeApp/                          # Compose Multiplatform 主模块
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/learnspark/
│       │   ├── App.kt
│       │   ├── core/
│       │   │   ├── network/             # Ktor + Auth
│       │   │   ├── security/            # SecureStorage expect + BiometricAuth expect
│       │   │   ├── sync/                # SyncEngine（冲突副本）
│       │   │   ├── di/                  # Koin
│       │   │   └── util/
│       │   ├── data/
│       │   │   ├── db/                  # SQLDelight
│       │   │   ├── api/                 # Ktor API 客户端
│       │   │   └── model/
│       │   ├── ai/                      # AiProvider 接口 + AiRouter
│       │   ├── features/                # auth / plan / submission / knowledge / gamification / dashboard
│       │   └── shared/                  # theme / navigation / components
│       │
│       ├── androidMain/kotlin/com/learnspark/
│       │   ├── MainActivity.kt
│       │   ├── core/security/           # AndroidKeystoreStorage / AndroidBiometricAuth
│       │   ├── core/network/            # OkHttp + 证书锁定
│       │   └── core/db/                 # AndroidSqlDriverFactory
│       └── desktopMain/kotlin/com/learnspark/
│           ├── Main.kt
│           ├── core/security/           # DesktopJnaSecureStorage / DesktopBiometricAuth
│           ├── core/network/            # DesktopConfig
│           └── core/db/                 # JdbcSqlDriverFactory
│
├── server/                              # 后端 Spring Boot（app-api / app-worker 共享）
│
└── README.md
```

> **关键差异**：
> - 无 `iosApp/` 和 `iosMain/`
> - 无 `wasmJsMain/`
> - Desktop 安全存储为 `DesktopJnaSecureStorage.kt`
> - `build.gradle.kts` 添加 JNA 依赖

---

## 十、开发路线图

| 阶段 | 周期 | 核心交付 | 里程碑 | 验证点 |
|------|------|---------|--------|--------|
| **阶段零** PoC 验证 | 2 周 | KMP / SQLDelight / JNA 三份 PoC 报告 | M0：决策点 | 是否继续 |
| **阶段一** 核心业务与同步 | 5 周 | KMP 工程化 + 后端安全 + 冲突副本同步 + 业务闭环 | M1：双端离线可用 | 内部用户试用 |
| **阶段二** 后端双进程 + 文件 | 4 周 | app-api/app-worker 拆分 + Tika + OCR + 知识库 | M2：稳定文件处理 | 大文件压测 |
| **阶段三** 打磨与发布 | 3 周 | 数据迁移 + 部署 + 体验 + 双端打包 | M3：全功能发布 | 正式上线 |
| **阶段四** 稳定性验证 | 1 周 | 压测 + bug 修复 + 灰度 | M4：稳定版 | 内部灰度 |
| **合计** | **14 周（3.5 个月）** | — | — | — |

### 10.1 关键缓冲与决策点

- **阶段零末尾**（第 2 周末）：JNA 兼容性 PoC 失败（某个平台调不通）→ 评估降级方案或砍 Desktop 端 Linux 支持
- **阶段一末尾**（第 7 周末）：双端可离线 + 同步，PM 评审冲突副本体验
- **阶段二末尾**（第 11 周末）：双进程压测，验证 worker 挂掉不影响 API
- **每个阶段结束后**：1 天回顾会议，更新下阶段工期估算

### 10.2 资源需求

| 资源 | 数量 | 说明 |
|------|------|------|
| 开发设备 | **全员 Windows / macOS / Linux 均可** | 无需采购 Mac |
| CI/CD | **GitHub Actions Ubuntu runner** | 编译速度比 macOS runner 快 3-5 倍 |
| 后端 / Android 工程师 | 各 1 名 | 共 2 人 |
| 全栈 / KMP 主力 | 1-2 名 | 承担 commonMain + Desktop |
| Android Compose 专项 | 1 名 | 熟悉 Android 互操作 + Keystore |
| **总人力** | **4-5 人** | — |

> 4-5 人 + Ubuntu runner 是**最低配置**。如果人手不够，先砍端（先 Android 单端，Desktop 延后到下一版）。

---

## 十一、迁移策略

### 11.1 现有代码处理

| 组件 | 处理方式 |
|------|---------|
| 后端 Spring Boot | **保留**，增量增强（拆分双进程） |
| 后端实体/DTO/Repository | **保留**，新增 version 字段 |
| 后端 Service/Controller | **保留**，新增同步端点 |
| MySQL 数据库 | **保留**，Flyway 增量迁移 |
| 前端 Vue3 | **保留 + 增强**，**不重写** |
| 前端 Capacitor/Android | **归档** |
| Docker Compose | **保留 + 增加 app-worker 服务** |

### 11.2 数据迁移

**问题**：旧版 Vue3 客户端使用 sql.js + IndexedDB 本地缓存数据。如果用户在旧版离线时写入了数据但没上传到服务端，新版直接登录会拿不到。

**方案**：

#### 11.2.1 旧版数据导出脚本

在旧版 Vue3 Web 端添加「数据导出」入口：

```typescript
// 旧版 Web 端：src/utils/exportOldData.ts
export async function exportOldLocalData(): Promise<ExportPayload> {
    const db = await getLocalDb()
    const tables = ['projects', 'phases', 'tasks', 'submissions', 'knowledge_entries']
    const payload: Record<string, any[]> = {}
    for (const t of tables) {
        payload[t] = db.exec(`SELECT * FROM ${t}`)[0]?.values || []
    }
    return {
        exportedAt: new Date().toISOString(),
        version: 1,
        data: payload
    }
}
```

#### 11.2.2 服务端导入脚本

用户在新客户端首次登录时，引导用户上传旧版导出的 JSON：

```
新客户端首次启动
    ↓
检测：是否有旧版导出文件？
    ├─ 是 → 提示用户「检测到旧版数据，是否上传？」
    │       ├─ 用户确认 → POST /api/v1/migration/import
    │       └─ 用户跳过 → 直接进入新版本
    └─ 否 → 直接进入新版本
```

服务端 `/api/v1/migration/import` 端点逻辑：
- 校验 user_id 匹配
- 字段映射：旧版 schema → 新版 schema
- 冲突处理：服务端已有数据 → 以服务端为准，导入数据只补充缺失记录
- 返回：导入成功/失败/跳过的记录数

#### 11.2.3 数据清洗规则

```
旧版字段 → 新版字段
─────────────────────────────────
projects.cover_color (hex)       →  projects.cover_color (不变)
projects.is_ai_generated (0/1)   →  projects.is_ai_generated (boolean)
phases.project_id                →  phases.project_id (不变)
tasks.phase_id                   →  tasks.phase_id (不变)
submissions.content_md           →  submissions.content (统一字段)
knowledge_entries.title (无)     →  knowledge_entries.title (从首行提取)
```

### 11.3 并行期

- KMP 开发期间，Vue3 版本继续使用
- KMP 版本就绪后，在 Vue3 顶部加「下载新版本客户端」横幅
- 过渡期 4-6 周，观察用户迁移情况
- 旧版下线条件：连续 2 周活跃用户 < 总用户的 5%

### 11.4 降级方案

#### 11.4.1 Web 端降级

KMP 不重写 Web 端，**Web 端不存在「加载失败白屏」**问题。Vue3 + PWA 已生产运行。

#### 11.4.2 KMP 端降级

- **登录失败**：服务端不可达 → 显示「当前为离线模式，可使用本地缓存数据」+ 离线指示器
- **同步失败**：网络断开 → 数据写入本地，标记 `is_dirty=1`，联网后自动重试
- **冲突**：用户收到 Toast「检测到冲突，已为您保留本地版本为副本」
- **生物识别不可用**：降级到密码登录
- **AI 不可用**：模板兜底（DeepSeek 限流 / 故障时）
- **后端解析 worker 挂掉**：文件上传正常返回 PENDING，worker 恢复后自动继续

---

## 十二、风险与对策

| 风险 | 等级 | 对策 |
|------|------|---------|
| **数据丢失** | 高 | **冲突副本策略** + 完整 UX 流程（通知 / 视觉 / 操作） |
| **后端 OOM 雪崩** | 中 | **双进程物理隔离** + 定时轮询（5 秒）而非监听 |
| **进程间通信复杂** | 中 | **不引入消息队列**，MySQL 状态机 + 轮询足够 |
| Desktop JNA 在 Linux 不可用 | 中 | **降级方案**：加密文件 + 主密码（PBKDF2 600k + AES-256-GCM） |
| 同步冲突副本数量膨胀 | 低 | 30 天未处理自动清理 + 设置页手动清理 |
| 桌面端 Linux 用户量小 | 低 | 阶段一加 Linux 端冒烟测试（GNOME / Server / WSL） |
| 冲突副本用户看不懂 | 中 | **解决**：通知 + 视觉区分 + 三个操作按钮 |
| 用户不处理冲突副本 | 低 | 通知中心提醒 + 30 天自动清理 |
| 轮询延迟 5 秒是否够 | 低 | 解析任务分钟级，5 秒完全可接受；可配置（PROD 调优） |
| 多 worker 实例抢任务 | 中 | `SELECT ... FOR UPDATE` 悲观锁 |
| 团队 KMP 经验不足 | 中 | 阶段零 PoC 培训；1-2 名核心成员先学 KMP |

---

## 十三、验收标准

### 13.1 功能验收

- [ ] Desktop（Windows/macOS/Linux）+ Android 双端均可登录使用
- [ ] Web 端（Vue3）继续可用，不强制升级
- [ ] 双端数据通过服务端同步（单向上传 + Pull 拉取）
- [ ] **断网后离线编辑数据不丢**：联网时冲突副本自动生成并上传
- [ ] **冲突时 UI 提示「已为您保留本地版本为副本」**（Snackbar + 通知中心双通道）
- [ ] **冲突副本在列表中琥珀色置顶**（视觉明显区分）
- [ ] **冲突详情页三个按钮可正常操作**（合并 / 保留 / 删除）
- [ ] **字段级合并对话框可用**（用户能逐字段选择保留哪个版本）
- [ ] **30 天未处理的冲突副本自动清理**
- [ ] AI 路线生成 + 任务审核功能正常（云端 API）
- [ ] 知识库支持 PDF/Word/Excel/PPT/MD/TXT/HTML/图片上传与搜索
- [ ] 生物识别解锁正常（Android BiometricPrompt / Desktop PIN）
- [ ] 设备管理可查看/注销已登录设备
- [ ] 旧版数据可平滑迁移

### 13.2 安全验收

- [ ] 所有通信走 HTTPS
- [ ] **RefreshToken 存储在 Keystore / JNA→系统 API**（不再有自写 AES 密钥明文）
- [ ] AccessToken 自动刷新无感（1 小时过期）
- [ ] 异地设备登录需重新认证
- [ ] Desktop 在 Windows/macOS/Linux 三平台密钥均加密存储

### 13.3 性能验收

- [ ] 双端冷启动 < 3 秒
- [ ] 同步 100 条变更 < 5 秒
- [ ] **app-worker 解析 50MB PDF 不影响 app-api 响应时间**
- [ ] **app-worker 崩溃后 app-api 仍能正常登录、CRUD**
- [ ] 文件上传 + 解析 < 30 秒（10MB 以内）
- [ ] 离线操作响应 < 100ms

### 13.4 稳定性验收

- [ ] app-api / app-worker 独立重启互不影响
- [ ] **app-worker 进程崩溃后重启，自动处理积压的 PENDING 任务**（轮询机制保障）
- [ ] **MySQL 状态机正确流转**：`PENDING → PROCESSING → READY/FAILED`
- [ ] **JNA 路径在 Windows / macOS / Ubuntu GNOME / Ubuntu Server / WSL2 / Docker 六环境全部通过**
- [ ] **Linux 无 GUI 环境降级到加密文件 + 主密码方案可用**
- [ ] **降级模式下 UI 顶部黄色提示条可见**：「当前环境不支持系统级凭据存储，已启用主密码保护模式」
- [ ] **轮询间隔可配置**（生产环境通过配置文件调整）

### 13.5 迁移验收

- [ ] 旧版数据导出 JSON 格式规范、字段完整
- [ ] 新版导入端点成功率 > 99%（边界用例测试）
- [ ] 导入完成后用户数据与服务端一致
- [ ] 旧版下线后无用户数据丢失投诉

---

## 十四、v4 路线图（不做、留给后续）

| 模块 | v4 计划 | 备注 |
|------|---------|------|
| iOS 端 | KMP 加 iosMain，复用 80% commonMain | 单独 1-2 月迭代 |
| 端侧 AI 推理 | Android TFLite 优先 | 先做「任务难度评估」单一场景 |
| 真正的双向同步 | CRDT（Yjs / Automerge） | 解决字符级合并 |
| Web 端 KMP Wasm | 待 Compose for Web 正式 Stable | 评估迁移 ROI |
| 端到端加密同步 | 服务端无法读取的笔记内容 | 用户隐私敏感场景 |
| 字段级冲突合并 UI | 复杂合并的友好展示 | 配合 CRDT |
| OCR 微服务 | 独立 Python 服务 | Tesseract → PaddleOCR |
| 协作功能 | 多用户编辑同一项目 | v4 末期评估 |
| 离线语音输入 | Web Speech API + 平台原生 | 学习场景延伸 |

---

## 十五、总结

本方案的核心决策：

1. **KMP 双端（Android + Desktop）**：砍 Web Wasm、砍 iOS，聚焦最稳的两端
2. **14 周工期**，前 2 周纯 PoC（含 JNA 兼容性 + 降级方案验证）
3. **冲突副本同步 + 完整 UX 流程**：通知 + 视觉区分 + 三个操作按钮（合并/保留/删除）
4. **后端双进程 + 定时轮询**：不引入 CDC/消息队列，MySQL 状态机 + 5 秒轮询
5. **JNA 系统 API + Linux 降级方案**：覆盖 Windows / macOS / Linux（含 Server/WSL/Docker）6 种环境
6. **不引入 Redis/RabbitMQ**：保持基础设施简单
7. **资源全解放**：4-5 人 + Ubuntu CI runner，无需 Mac 设备
8. **降级方案明确**：Web 不重写 / KMP 离线模式 / 冲突副本 UX / AI 模板兜底 / worker 进程隔离 / Linux 主密码降级

**成功标准**：

- 14 周内上线一个**真正能用的双端原生客户端** + **Web 端稳定运行**
- **用户数据零丢失**：冲突副本 + 完整 UX 流程保底
- **后端稳定**：app-api/app-worker 物理隔离 + 轮询机制无单点故障
- **跨平台覆盖**：Windows / macOS / Linux（含 Server/WSL/Docker）/ Android 全可用
- **工程质量过关**：CI/CD 跑通、自动化测试覆盖率 > 60%、崩溃率 < 0.5%
- **v4 路线图清晰**：iOS 端、CRDT、端侧 AI、端到端加密有明确技术路径

**一句话**：技术服务于产品，过程容错优于表面正确——**轮询比监听更简单、降级方案比裸跑更安全、UX 流程比 Toast 更友好**。
