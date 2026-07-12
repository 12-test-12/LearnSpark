# AI 智能生成学习路线 — 实现计划

## 实现进度（2026-07-12 核实）

| # | 步骤 | 文件 | 状态 |
|---|------|------|------|
| 1 | ErrorCode 扩展 | `ErrorCode.java` | ✅ 已完成（AI_GENERATE_FAILED 30004, SEARCH_FAILED 30005） |
| 2 | PhaseRepository.deleteByProjectId | `PhaseRepository.java` | ✅ 已完成 |
| 3 | TaskRepository.deleteByProjectId | `TaskRepository.java` | ❌ 待实现 |
| 4 | SearchConfig DTO | `ai/dto/SearchConfig.java` | ❌ 待创建 |
| 5 | AiConfigService.getSearchConfig | `AiConfigService.java` | ❌ 待实现 |
| 6 | SearchResultItem DTO | `ai/search/dto/SearchResultItem.java` | ❌ 待创建 |
| 7 | BingSearchService | `ai/search/BingSearchService.java` | ❌ 待创建 |
| 8 | SearchQueryService | `ai/search/SearchQueryService.java` | ❌ 待创建 |
| 9 | DeepSeekPlanClient | `ai/plan/DeepSeekPlanClient.java` | ❌ 待创建 |
| 10 | GeneratePlanResponse DTO | `ai/plan/dto/GeneratePlanResponse.java` | ❌ 待创建 |
| 11 | PlanGenerationService | `ai/plan/PlanGenerationService.java` | ❌ 待创建 |
| 12 | ProjectController generate-plan 端点 | `ProjectController.java` | ❌ 待实现 |
| 13 | 前端 plan.ts | `frontend/src/api/plan.ts` | ❌ 待创建 |
| 14 | 前端 GeneratePlan.vue 向导 | `frontend/src/views/GeneratePlan.vue` | ❌ 待创建 |
| 15 | 前端路由 + 入口按钮 | `router/index.ts` + `ProjectDetail.vue` + `Projects.vue` | ❌ 待实现 |
| 16 | 后端编译验证 | `mvn compile` | ❌ 待执行 |
| 17 | 前端构建验证 | `npm run build` | ❌ 待执行 |

## Context（背景）

用户反馈"AI 没有根据上传的东西来进行任务设置"。经排查，该功能（任务流程拆分.md 阶段二模块 2.3 + 2.1）**从未实现**：`TaskService`/`ProjectService` 只有手动 CRUD，`DeepSeekReviewService` 只用于审核提交，前端无 AI 生成入口。数据库 `projects.is_ai_generated` 字段和前端"AI 生成"标签已就位，但生成逻辑缺失。

本计划实现完整版（含 Bing 搜索）：用户上传 .md/.txt 资料 + 填目标 + 可选联网搜索 → DeepSeek 生成结构化 phases+tasks → 写库 → 前端向导预览。

## 复用的现有基础设施

- **AI 配置**：`AiConfigService.getDeepSeekConfig(userId)` → `DeepSeekConfig(apiKey, baseUrl, model)` 或 null。`UserAiConfig` 有 `searchApiKeyEncrypted` 字段（需新增 public 方法暴露搜索 key 解密）
- **DeepSeek 调用模式**：参考 `DeepSeekReviewService`（RestClient + SimpleClientHttpRequestFactory + header Bearer + body{model,messages,response_format,temperature} + 解析 choices[0].message.content + extractJson 去除 ```json 包裹）
- **文件解析**：`MarkdownParserService.parseMarkdown/parsePlainText` → `ParseResult(title, content, ...)`，content 是纯文本。只支持 .md/.txt
- **写入**：直接用 `PhaseRepository`/`TaskRepository`（不走 PhaseService.create，避免逐条 verifyProjectOwnership 冗余校验）
- **前端上传**：`request.ts` 已修复，FormData 自动 multipart；参考 `knowledge.ts` 的 `uploadKnowledge`
- **错误处理**：`BusinessException` + `ErrorCode` 枚举

## 后端实现

### 1. ErrorCode 扩展
文件：`src/main/java/com/learnspark/common/exception/ErrorCode.java`

在 AI 30xxx 段（现有 30001-30003）追加：
```java
AI_GENERATE_FAILED(30004, "AI 生成学习路线失败"),
SEARCH_FAILED(30005, "网络搜索失败"),
```
注：未配置 AI Key 复用现有 `AI_CONFIG_NOT_SET(30001)`。

### 2. AiConfigService 扩展 — 暴露搜索 key
文件：`src/main/java/com/learnspark/ai/service/AiConfigService.java`

新增 public 方法（复用 private `decryptSafely`）：
```java
@Transactional(readOnly = true)
public SearchConfig getSearchConfig(String userId) {
    // 同 getDeepSeekConfig 模式：取 config → 本地模式/null 返回 null → 解密 searchApiKeyEncrypted
}
```
新增 DTO：`src/main/java/com/learnspark/ai/dto/SearchConfig.java` — `record SearchConfig(String apiKey, String provider) {}`

### 3. BingSearchService（任务 2.1.1）
新增：`src/main/java/com/learnspark/ai/search/BingSearchService.java`
新增：`src/main/java/com/learnspark/ai/search/dto/SearchResultItem.java` — `record SearchResultItem(String title, String url, String snippet) {}`

- GET https://api.bing.microsoft.com/v7.0/search，header `Ocp-Apim-Subscription-Key`
- 参数：q, count=5, mkt=zh-CN
- 解析 `response.webPages.value[]` → `List<SearchResultItem>`
- snippet 截断 300 字符。RestClient 模式同 DeepSeekReviewService

### 4. SearchQueryService（任务 2.1.2）
新增：`src/main/java/com/learnspark/ai/search/SearchQueryService.java`

- 调 DeepSeek（复用 getDeepSeekConfig），System Prompt 要求返回 `{"queries": ["q1","q2",...]}`
- 输入：goal + materialSummary（截断 2000 字）
- 失败降级返回空 List（不抛错）

### 5. DeepSeekPlanClient（任务 2.3.1 调用层）
新增：`src/main/java/com/learnspark/ai/plan/DeepSeekPlanClient.java`

- 独立于 ReviewService（readTimeout 60s，路线生成 prompt 更长）
- System Prompt 要求返回 JSON `{ phases: [{ name, objective, tasks: [{ day_number, title, description, verification_criteria }] }] }`
- temperature 0.4，response_format json_object
- 401 抛 AI_KEY_INVALID，其它抛 RuntimeException（由上层包装）

### 6. PlanGenerationService（任务 2.3.1 核心）
新增：`src/main/java/com/learnspark/ai/plan/PlanGenerationService.java`

`@Transactional` 方法 `generatePlan(userId, projectId, files, useWebSearch, targetDays, replaceMode)`：
1. 校验项目归属（`projectRepository.findByIdAndUserId`）
2. 获取 DeepSeek 配置（null → 抛 AI_CONFIG_NOT_SET）
3. replaceMode=true 时先清理旧 phases/tasks（新增 repository 方法 `deleteByProjectId`）
4. 解析上传文件 → 合并纯文本（`markdownParserService`，单文件截断 4000 字，总 12000 字）
5. useWebSearch 时安全搜索（getSearchConfig → generateQueries → bingSearch，失败降级空串）
6. 拼 userPrompt（goal + dailyHours + targetDays + 资料摘要 + 搜索摘要）
7. 调 DeepSeekPlanClient → JSON
8. parsePlan（JsonNode 手动取值，容错：字段缺失补默认值，phases 空抛 AI_GENERATE_FAILED）
9. persistPlan（phaseRepository.save + taskRepository.save 批量写入）
10. 标记 `project.isAiGenerated = true` 并保存
11. 返回 `GeneratePlanResponse(projectId, phases, tasks, phaseCount, taskCount)`

### 7. DTO
新增：`src/main/java/com/learnspark/ai/plan/dto/GeneratePlanResponse.java`
```java
@Data @Builder @AllArgsConstructor
public class GeneratePlanResponse {
    private String projectId;
    private List<PhaseResponse> phases;   // 复用 plan.dto.PhaseResponse
    private List<TaskResponse> tasks;     // 复用 plan.dto.TaskResponse（扁平）
    private int phaseCount;
    private int taskCount;
}
```

### 8. ProjectController 新端点
文件：`src/main/java/com/learnspark/plan/controller/ProjectController.java`

```java
@PostMapping("/{id}/generate-plan")
public ApiResult<GeneratePlanResponse> generatePlan(
        @CurrentUser String userId, @PathVariable String id,
        @RequestParam(value="files", required=false) List<MultipartFile> files,
        @RequestParam(value="useWebSearch", defaultValue="false") boolean useWebSearch,
        @RequestParam(value="targetDays", required=false) Integer targetDays,
        @RequestParam(value="replaceMode", defaultValue="true") boolean replaceMode) {
    return ApiResult.success(planGenerationService.generatePlan(...));
}
```
注入 `PlanGenerationService`。multipart + @RequestParam 混用，Spring 自动解析表单字段。

### 9. Repository 扩展（清理旧数据）
文件：`src/main/java/com/learnspark/plan/repository/PhaseRepository.java` + `TaskRepository.java`
新增：`void deleteByProjectId(String projectId);`（replaceMode 用）

## 前端实现

### 10. API 封装
新增：`frontend/src/api/plan.ts`
```typescript
export function generatePlan(projectId, files: File[] = [], useWebSearch = false, targetDays?, replaceMode = true): Promise<GeneratePlanResult> {
  const formData = new FormData()
  files.forEach(f => formData.append('files', f))
  formData.append('useWebSearch', String(useWebSearch))
  if (targetDays) formData.append('targetDays', String(targetDays))
  formData.append('replaceMode', String(replaceMode))
  return request.post(`/projects/${projectId}/generate-plan`, formData, { timeout: 120000 })
}
```

### 11. 路由
文件：`frontend/src/router/index.ts` — 在 DefaultLayout children 中新增：
```typescript
{ path: 'projects/:id/generate-plan', name: 'generate-plan',
  component: () => import('@/views/GeneratePlan.vue'), meta: { title: 'AI 生成路线' } }
```

### 12. GeneratePlan.vue 向导（任务 2.3.3）
新增：`frontend/src/views/GeneratePlan.vue`

NSteps 步骤：
- **Step1 目标时长**：显示项目名/目标（只读，onMounted 调 getProject），NInputNumber 输入 targetDays(1-90)
- **Step2 上传资料**：NUpload 拖拽区，accept=".md,.txt"，多选，可删除
- **Step3 搜索开关**：NSwitch（useWebSearch）；onMounted 调 getAiConfig 检查 hasSearchKey，未配置时 NAlert 警告并禁用
- **Step4 生成中**：NSpin + "AI 正在分析..."，调 generatePlan，成功→Step5，失败→NResult error
- **Step5 结果预览**：NCollapse 展示 phases，每个 phase 内 tasks 列表（标题+描述+验证标准）；底部"重新生成"（回 Step1，replaceMode=true）+"查看项目"（跳转 project-detail）

### 13. 入口按钮
- `frontend/src/views/ProjectDetail.vue`：在"添加任务"按钮旁加"AI 生成路线"按钮（SparklesOutline 图标），跳转 `name: 'generate-plan'`
- `frontend/src/views/Projects.vue`：项目卡片操作区加"AI 生成"次要按钮

## 降级与异常策略

| 场景 | 行为 |
|------|------|
| 未配置 DeepSeek Key / 本地模式 | 抛 AI_CONFIG_NOT_SET，终止 |
| DeepSeek 401 | 抛 AI_KEY_INVALID |
| DeepSeek 超时/其它错误 | 抛 AI_GENERATE_FAILED |
| AI JSON 解析失败 | 抛 AI_GENERATE_FAILED（log 记录原始 content） |
| 启用搜索但未配置搜索 Key | 降级：跳过搜索，正常生成 |
| Bing 搜索失败 | 降级：跳过搜索，正常生成 |
| 文件解析失败/类型不支持 | 跳过该文件，继续其它 |

## 验证方式

1. **后端编译**：`mvn compile`（D:\LearnSpark）
2. **前端构建**：`npm run build`（D:\LearnSpark\frontend）
3. **端到端**：
   - 设置页配置 DeepSeek Key（可选配置 Bing Key）
   - 创建一个项目（如"Vue3 进阶"）
   - 项目详情页点"AI 生成路线" → 上传一个 .md 资料 → 设置 14 天 → 不开搜索 → 生成
   - 验证：生成后 phases+tasks 出现，项目标记"AI 生成"
   - 开启搜索重复（需配置 Bing Key），验证搜索摘要拼入
   - 未配置 DeepSeek Key 时访问 → 报"未配置 AI 密钥"
   - 重新生成 → 旧 phases/tasks 被清理后重新生成
