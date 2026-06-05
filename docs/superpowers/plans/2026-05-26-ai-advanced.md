# Phase 6: AI Agent Advanced 实现计划

> **For agentic workers:** Use superpowers:subagent-driven-development to implement.

**Goal:** 实现风险评估、AI 报告生成、需求拆解、智能排期、自然语言查询 5 大模块。

## 分组策略

| 组 | 模块 | 任务数 |
|------|------|--------|
| 6a | 风险评估 | 5 |
| 6b | AI 报告生成 | 4 |
| 6c | 需求拆解 | 5 |
| 6d | 智能排期 + NL 查询 | 6 |

---

### 6a: 风险评估

**规则引擎 `RiskAssessmentService`:**
- 标题关键词匹配（"紧急"/"生产bug"/"崩溃" → 高风险）
- 描述过短（<20字符 → 中风险）
- 截止日期<24h → 高风险
- 依赖任务数>3 → 中风险
- 指派人负载>80% → 中风险
- 综合评分 → 设置 `task.riskLevel` + `task.riskReason`

**API:**
- `POST /api/v1/ai/risks/{taskId}` → 手动评估单个任务
- `POST /api/v1/ai/risks/project/{projectId}` → 评估项目所有活跃任务
- `GET /api/v1/projects/{pid}/risks` → 获取项目风险列表（高/中/低分组）

**自动触发:**
- `@EventListener` 监听 TaskChangedEvent → 异步重新评估

**前端:**
- 风险面板组件 `RiskPanel.tsx` — 高/中/低分组卡片
- Dashbpard 集成显示项目风险

### 6b: AI 报告生成

**API:**
- `POST /api/v1/ai/reports/generate?type=weekly|sprint_review|project_summary`
- 调用 AI 基于: 项目统计 + 最近活动 + 任务分布 → 生成 Markdown 报告
- 返回 `{ markdown, title, generatedAt }`

**前端:**
- 报告生成按钮（在 ReportPage 中）
- Markdown 预览 + 编辑
- 复用现有 PDF/CSV 导出

### 6c: 需求拆解

**API:**
- `POST /api/v1/ai/breakdown/{taskId}` (SSE 流式)
- AI 读取任务描述 → 生成子任务树 JSON
- SSE 推送 `breakdown_preview` 事件

**前端:**
- 树形预览组件 `BreakdownPreview.tsx`
- 可编辑子任务字段、删除、调整层级
- 确认 → 批量创建子任务

### 6d: 智能排期 + NL 查询

**智能排期:**
- `POST /api/v1/ai/suggestions/scheduling/{sprintId}`
- AI 分析依赖+容量 → 返回排期建议 JSON
- 前端: 排期建议面板（表格 + 一键应用）

**NL 查询:**
- `POST /api/v1/ai/nl-query` (SSE)
- AI 生成只读 SQL → 安全校验 → 执行 → 格式化
- 前端: 聊天式输入 → 表格 + 图表推荐
