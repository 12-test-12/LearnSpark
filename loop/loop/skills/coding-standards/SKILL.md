---
name: "coding-standards"
description: "代码规范规则。制定覆盖代码风格、命名规范、架构设计、性能优化、安全防护的详细技术标准。当需要编写新代码、审查代码质量或建立项目编码规范时调用。在 loop engineering 流程的[C]修复前/[E]修复后两个步骤中必调，用于确认/复查代码符合规范。"
---

# 代码规范规则（Coding Standards）

覆盖代码风格、命名、架构、性能、安全的详细技术标准与实施细则。

## 与 loop engineering 联动

loop engineering 每轮迭代的 [C] 修复前 和 [E] 修复后 两个步骤必须调用本 skill：
- **[C] 修复前**：列出本轮要修改的代码，确认每处修改的目标函数行数、命名、中文注释计划
- **[E] 修复后**：复查实际修改是否合规，特别检查：函数 ≤ 20 行、注释解释"为什么"而非"做什么"、命名一致性

---

## 一、代码风格规范

### 1.1 通用格式

| 规则 | 标准 | 反例 |
|------|------|------|
| 缩进 | 2 空格（不混用 Tab） | `if(x){\t` |
| 行宽 | ≤ 120 字符 | 超长拼接 |
| 分号 | 必须加分号 | `const a = 1` |
| 引号 | 单引号优先，HTML 属性双引号 | 混用 |
| 花括号 | 必须用花括号包裹块 | `if(x) return;` |
| 尾逗号 | 多行对象/数组最后一个元素加逗号 | `[1,2,3]` |

### 1.2 注释规范

```javascript
// ✅ 正确：注释解释"为什么"
// Daniels 公式要求速度单位为 m/min，需将 km/h 转换
const v = distanceKm * 1000 / (timeSec / 60);

// ❌ 错误：注释复述代码"做什么"
// 把距离乘以 1000 除以时间
const v = distanceKm * 1000 / (timeSec / 60);
```

| 注释类型 | 格式 | 使用场景 |
|----------|------|----------|
| 函数说明 | `// 函数功能：____` | 每个函数上方 |
| 公式来源 | `// 来源：____《____》` | 引用外部公式 |
| 关键决策 | `// 选择____而非____，因为____` | 有多种方案时 |
| TODO | `// TODO: ____` | 待完成项 |
| 警告 | `// ⚠️ ____` | 需特别注意的边界 |

---

## 二、命名规范

### 2.1 命名约定

| 类型 | 风格 | 示例 |
|------|------|------|
| 变量 | camelCase | `weekVolume`, `todayAbs` |
| 常量 | UPPER_SNAKE | `HARD_TYPES`, `MAX_VOLUME` |
| 函数 | camelCase，动词开头 | `calcVdot()`, `renderPlan()` |
| 类/构造器 | PascalCase | `Store`, `PlanBuilder` |
| 布尔值 | is/has/can/should 前缀 | `isResidual`, `hasPrevData` |
| 事件处理 | on/handle 前缀 | `onPbChange()`, `handleClick()` |
| 私有 | _ 前缀 | `_internalCache` |

### 2.2 命名质量要求

```javascript
// ✅ 描述性命名
const residualDays = startDow;           // 清晰：残周占位天数
const absoluteDay = dayDiff + residualOffset; // 清晰：绝对天数
function calcBestVdot(pb) { }            // 清晰：计算最佳 VDOT

// ❌ 神秘命名
const r = startDow;                      // r 是什么？
const ad = dayDiff + ro;                 // ad? ro?
function p() { }                         // p 做什么？
```

---

## 三、架构设计规范

### 3.1 分层原则

```
数据层（STORE）    → 状态管理，单一数据源
  ↓
逻辑层（纯函数）   → calcVdot, generatePlan, evalReadiness
  ↓
渲染层（render*）  → renderToday, renderPlan, renderProfile
  ↓
交互层（事件）     → completeWorkout, skipWorkout, saveReadiness
```

| 层级 | 职责 | 禁止 |
|------|------|------|
| 数据层 | 存储状态 | 不含逻辑 |
| 逻辑层 | 纯计算 | 不操作 DOM |
| 渲染层 | 生成 HTML | 不修改状态 |
| 交互层 | 处理事件 | 不直接计算 |

### 3.2 SSOT 原则（Single Source of Truth）

```javascript
// ✅ 训练类型常量集中定义
const HARD_TYPES = new Set(['I','T','M', ...]);
const WORKOUT_NAMES = { I:'间歇跑', T:'乳酸阈值跑', ... };

// ❌ 散落各处的硬编码
if(type === 'I' || type === 'T') { } // 到处重复
const name = type === 'I' ? '间歇跑' : type === 'T' ? '阈值跑' : ...;
```

### 3.3 函数设计

| 规则 | 标准 | 说明 |
|------|------|------|
| 单一职责 | 一个函数只做一件事 | 超过 20 行考虑拆分 |
| 参数数量 | ≤ 3 个 | 超过用参数对象 |
| 提前返回 | 先处理边界，再处理主逻辑 | 避免深层嵌套 |
| 纯函数 | 相同输入→相同输出 | 渲染函数不修改状态 |
| 防御性编程 | 边界值校验 | null/undefined/空数组 |

```javascript
// ✅ 提前返回 + 单一职责
function calcBestVdot(pb){
  if(!pb) return 0;                    // 边界：无输入
  let best = 0;
  for(const e of EVENTS){
    if(!e.time) continue;              // 边界：无成绩
    const sec = parseTimeToSec(e.time);
    if(sec <= 0) continue;             // 边界：无效格式
    const v = calcVdot(e.km, sec);
    if(v > best) best = v;
  }
  return best;
}
```

---

## 四、性能优化规范

### 4.1 渲染性能

| 规则 | 说明 | 示例 |
|------|------|------|
| 局部更新优先 | 避免 `render()` 整页重建 | RPE 点击只切 class |
| 减少 DOM 操作 | 批量插入而非逐个 | 用字符串拼接后一次赋值 |
| 事件委托 | 同类事件用父级代理 | Tab 切换用 data-page |
| 防抖节流 | 高频事件做节流 | 滑块拖动用 debounce |

```javascript
// ✅ 局部更新：只切 RPE 状态
function selectRpe(v, weekIdx, dayIdx){
  day.rpe = v;
  document.querySelectorAll('.rpe-dot').forEach((el, i) => {
    el.classList.toggle('sel', (i+1) === v);  // 不调 render()
  });
}

// ❌ 整页重建：点击就 reRender
function selectRpe(v){
  STORE.rpeValue = v;
  render();  // 整页闪烁
}
```

### 4.2 计算性能

| 规则 | 说明 |
|------|------|
| 避免重复计算 | 缓存中间结果 |
| 提前终止 | 找到目标即 break |
| 惰性求值 | 需要时才计算 |
| 合理数据结构 | Set 用于查找，Array 用于遍历 |

```javascript
// ✅ 提前终止 + Set 查找
for(let w = todayWeek; w < plan.weeks.length; w++){
  for(let d = startDay; d < days.length; d++){
    if(HARD_TYPES.has(day.type)) return found; // 找到即返回
  }
}
```

---

## 五、安全防护规范

### 5.1 输入校验

```javascript
// ✅ 严格校验时间格式
function parseTimeToSec(str){
  if(!str) return 0;
  const s = String(str).trim();
  // 只允许数字+冒号
  if(!/^\d{1,2}(:\d{1,2}){0,2}$/.test(s)) return 0;
  // 范围校验
  if(sec < 30 || sec > 36000) return 0;
  return sec;
}
```

### 5.2 XSS 防护

| 规则 | 说明 |
|------|------|
| 不拼接用户输入到 innerHTML | 用 textContent 或转义 |
| 模板字符串中的变量需安全 | 确保不含 HTML 标签 |
| URL 参数需编码 | encodeURIComponent |

### 5.3 状态安全

| 规则 | 说明 |
|------|------|
| 不可变更新 | 不直接修改 STORE，用展开运算符 |
| 边界保护 | 数组访问前检查长度 |
| 类型检查 | 关键参数做 typeof 校验 |

---

## 六、坏味道速查表

| 坏味道 | 症状 | 处理方法 |
|--------|------|----------|
| 神秘命名 | 变量名无法表达含义 | 重命名为描述性名称 |
| 重复代码 | 相同逻辑多处出现 | 提取为共享函数 |
| 过长函数 | 超过 20 行 | 拆分为多个小函数 |
| 过大对象 | 一个对象职责过多 | 提取子对象 |
| 参数过多 | 超过 3 个参数 | 引入参数对象 |
| 嵌套过深 | 超过 3 层 | 提前返回 |
| 全局可变 | 全局变量被多处修改 | 改为局部或实体属性 |
| 基本类型偏执 | 用 string 表示有含义的数据 | 提取为对象 |
