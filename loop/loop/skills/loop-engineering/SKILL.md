---
name: "loop-engineering"
description: "Iterative self-review engineering loop for code quality. Invoke when user asks for 'loop engineering', 'iterative debugging', 'self-review iteration', or wants to continuously improve code by finding and fixing bugs across multiple dimensions. Must coordinate with coding-standards, self-introspection, content-reviewing, and prompt-writing skills per the 联动技能矩阵."
---

# Loop Engineering（迭代自审工程）

对代码进行多轮迭代式自我审查与修复，每轮聚焦不同维度，逐步消除逻辑错误、UX 问题和性能隐患。

## 适用场景

- 用户要求"迭代 → 自我审查 → 迭代"循环
- 代码已能运行但需要持续打磨质量
- 需要系统性发现并修复边界场景 Bug
- 大型功能开发完成后的全面质量加固

## 核心流程

### 1. 建立扫描维度矩阵

每轮迭代聚焦一个维度，避免发散。推荐维度（按优先级排序）：

| 轮次 | 维度 | 检查重点 |
|------|------|----------|
| R1 | 核心逻辑正确性 | 算法是否正确、公式来源是否可靠、边界值是否覆盖 |
| R2 | 状态管理与持久化 | 数据是否跨会话/跨操作丢失、编辑状态是否可回退 |
| R3 | 交互一致性 | 事件冒泡、按钮重复触发、输入框失焦问题 |
| R4 | UI 一致性与边界 | 视觉一致性、空状态、极端数据展示 |
| R5 | 性能与可访问性 | 局部更新 vs 整页 reRender、CSS 变量有效性 |
| R6 | 极端边界场景 | 跨年/跨月日期、超大数据量、零值/负值 |

### 2. 每轮执行步骤（审查 → 写提示词 → 迭代 → 下一轮审查）

```
[Plan] 制定本轮计划
            ↓
[1] 审查现状（Read + Grep）           ← 工具：Read / Grep
            ↓
[2] 写本轮提示词 ──→ 联动 skill: prompt-writing
            ↓                            让模型为本轮修复设计 5 层结构 prompt
[3] 按 prompt 迭代执行（Edit）         ← 工具：Edit（按 prompt 输出）
            ↓
[4] 修复后规范复审 ──→ 联动 skill: coding-standards
            ↓                            复查新代码（中文注释、≤20 行、命名）
[5] 浏览器实测验证（web 项目）          ← 工具：browser_navigate / take_screenshot
            ↓
[6] 控制台错误检查                     ← 工具：browser_console_messages
            ↓
[7] 本轮根因反思 ───→ 联动 skill: self-introspection
            ↓                            五问法分析每个修复的根因
[8] 报告质量审查 ───→ 联动 skill: content-reviewing
            ↓                            审查本轮修复报告的准确性/逻辑性
[Check] 经验沉淀 ──→ 联动 skill: self-introspection
            ↓                             Bug 模式更新到 project_memory.md
[下一轮] → 回到 [1] 审查
```

### 3. 终止条件

- 连续一轮未发现新问题
- 所有维度已扫描完毕
- 浏览器实测无 JS 错误且核心流程跑通

## 联动技能矩阵（必读）

loop engineering 不是单一 skill 的工作流，而是**多 skill 协同**。每轮迭代必须在指定步骤调用以下 skill：

| 步骤 | 联动 skill | 调用目的 | 必调用？ |
|------|------------|----------|----------|
| [2] 写本轮提示词 | **prompt-writing** | 让模型为本轮修复设计 5 层结构 prompt（角色-任务-规则-示例-异常），按 prompt 驱动后续执行 | ✅ 必调 |
| [4] 修复后复审 | **coding-standards** | 复查新代码（中文注释、≤20 行、命名一致性） | ✅ 必调 |
| [7] 每轮根因 | **self-introspection** | 用五问法做根因分析 | ✅ 必调 |
| [8] 报告质量 | **content-reviewing** | 审查修复报告的准确性/逻辑性/可读性 | ✅ 必调 |
| [Check] 经验沉淀 | **self-introspection** | 提炼 Bug 模式，写入 project_memory.md | ✅ 必调 |

**注意**：联动 skill 是 loop engineering 的**强制子步骤**，不能跳过。漏调等于本轮自审不完整。

## 必要技能清单

### 技能 A：静态代码审查

- 用 `Grep` 搜索已知坏味道模式：未定义 CSS 变量、全局可变状态、魔法数字
- 用 `Read` 精读关键函数，重点检查：边界条件、参数校验、异步顺序
- 检查公式来源：是否有注释标明出处（如 Daniels 公式）

### 技能 B：浏览器实测验证

- 用 `browser_navigate` 打开页面
- 用 `browser_take_screenshot` 截图每个 Tab
- 用 `browser_evaluate` 执行交互（点击、滑动、输入）
- 用 `browser_console_messages` 检查 JS 错误
- 关键：`browser_evaluate` 的 script 参数是字符串，不是函数

### 技能 C：局部更新优化

优先使用局部 DOM 更新替代整页 reRender：
- 切换 class 而非重建 HTML
- 更新 textContent 而非 innerHTML
- 仅在必要时触发 `render()`

### 技能 D：修复记录追踪

每轮结束时记录：
```
### 第 N 轮：[维度名]
- 修复项 1：问题描述 → 修复方案
- 修复项 2：...
- 验证结果：浏览器实测通过/失败
```

## 常见 Bug 模式清单

| 模式 | 症状 | 检查方法 |
|------|------|----------|
| 全局可变状态 | 不同实体共享同一状态 | 搜索 `STORE.xxx` 全局变量，确认是否应改为实体属性 |
| 未定义 CSS 变量 | 样式回退异常 | Grep `var(--` 对比 `:root` 定义 |
| 事件冒泡 | 点击按钮触发父级 | 检查 `onclick` 是否有 `event.stopPropagation()` |
| 日期时区偏移 | `getDay()` 返回值非预期 | 检查 `setHours(0,0,0,0)` 是否归零 |
| IME 输入失焦 | 中文输入被打断 | `oninput` 改 `onchange`，或只更新数字不 reRender |
| 公式参数错误 | 计算结果偏离常识 | 对照原始论文/公式验证参数单位 |

## 注意事项

- 每轮只改一个维度，避免发散
- 修复前先读文件，不要凭记忆改
- 浏览器实测是必须的，不能只靠静态分析
- 遇到需要用户决策的问题用 AskUserQuestion，不要自作主张
