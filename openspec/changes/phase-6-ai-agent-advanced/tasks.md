# Phase 6: Tasks

## Task Breakdown

- [ ] POST /ai/breakdown/{taskId}（SSE 流式 → breakdown_preview 事件）
- [ ] 前端：树形拆解预览（可折叠、可编辑每个子任务字段）
- [ ] 前端：拖拽调整子任务层级和顺序
- [ ] 用户确认 → 批量创建子任务（POST /tasks/batch）

## Risk Assessment

- [ ] 实现 RiskAssessmentService（标题分析 + 描述完整性 + 时间 + 依赖链 + 负载）
- [ ] 任务创建/更新时自动触发评估（@EventListener）
- [ ] POST /ai/risks/{taskId}（手动触发）
- [ ] GET /projects/{pid}/risks（项目风险面板）
- [ ] 前端：风险面板（高/中/低 分组，风险项目列表 + 原因说明）

## Smart Scheduling

- [ ] POST /ai/suggestions/scheduling/{sprintId}
- [ ] AI 分析：依赖拓扑排序 + 历史速度 + 容量判断
- [ ] 返回排期建议 JSON（任务 → 指派人 + 起止日期 + 理由）
- [ ] 前端：排期建议面板（表格展示 + 一键应用）

## AI Report Generation

- [ ] POST /ai/reports/generate?type=weekly|sprint_review|project_summary
- [ ] AI 基于项目数据 + 最近活动生成报告 Markdown
- [ ] 报告包含：关键指标、完成项、风险项、下周计划、建议
- [ ] 前端：报告预览 + 编辑 + PDF/CSV 导出

## Natural Language Query

- [ ] POST /api/v1/ai/nl-query（SSE 流式）
- [ ] AI 生成只读 SQL（禁止 INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE）
- [ ] 执行 SQL → 结果 + AI 格式化建议 + 图表推荐
- [ ] 前端：聊天式输入 → 数据结果展示（表格 + 图表）

## Verification

- [ ] Epic 拆解：创建 Epic → AI 拆解 → 预览 → 确认 → 子任务创建成功
- [ ] 风险评估：创建高风险描述任务 → 自动标记 risk_level=high
- [ ] 排期建议：AI 给出排期 → 成员负载不超 100%
- [ ] 报告生成：AI 周报内容合理、数据准确
- [ ] 自然语言查询：SQL 安全校验（注入攻击拒绝）
