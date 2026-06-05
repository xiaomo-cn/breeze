# Phase 6: Design

## AI Task Breakdown

```
用户: "把 Epic '用户中心改版' 拆成子任务"
    ↓
AI 读取 Epic 描述 → RAG 检索相关历史任务
    ↓
LLM 生成子任务列表 (JSON):
  [ { title, type, priority, estimatedHours, children: [...] } ]
    ↓
SSE 推送 breakdown_preview 事件
    ↓
前端展示树形拆解预览（可编辑、删除、调整优先级）
    ↓
用户确认 → 批量创建子任务
```

## Risk Assessment

```
触发时机:
  - 任务创建时自动评估
  - 任务更新时自动重新评估
  - 手动 POST /ai/risks/{taskId}

评估维度:
  - 标题关键词（"紧急"、"生产bug" → 高风险）
  - 描述长度过短（需求不清楚 → 中风险）
  - 截止日 < 24h（时效风险）
  - 依赖任务数量 > 3（依赖链风险）
  - 指派人负载 > 80%（资源风险）

风险等级: 低 / 中 / 高

存储: tasks.risk_level + tasks.risk_reason
```

## Smart Scheduling

```
输入: Sprint backlog (任务列表 + 成员列表)
    ↓
AI 分析:
  - 任务依赖关系 → 拓扑排序
  - 成员历史速度 → 预估工期
  - 成员当前负载 → 容量判断
    ↓
输出: 排期建议
  [ { taskId, suggestedAssignee, suggestedStartDate, suggestedEndDate, reason } ]
```

## Natural Language Query

```
用户输入: "这周完成的任务按人统计"
    ↓
AI 生成 SQL:
  SELECT u.display_name, COUNT(*) as completed
  FROM tasks t JOIN users u ON t.assignee_id = u.id
  WHERE t.status = 'done'
    AND t.resolved_at >= '2026-05-18'
  GROUP BY u.id
  ORDER BY completed DESC
    ↓
执行 SQL (read-only, 限制行数)
    ↓
AI 格式化结果 + 推荐图表类型
    ↓
前端渲染图表（Recharts）
```
