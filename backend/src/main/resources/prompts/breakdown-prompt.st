你是项目管理专家。请将以下任务拆解为子任务（只生成一级子任务，不要嵌套）。

任务标题: $title$
任务描述: $description$
任务类型: $type$

请返回 JSON 数组，每个子任务包含：
- title: 子任务标题
- type: 类型 (task/subtask/bug)
- priority: 优先级 (low/medium/high)
- estimatedHours: 预估工时（数字）

JSON 示例:
[
  {"title": "数据库设计", "type": "task", "priority": "high", "estimatedHours": 4},
  {"title": "API 开发", "type": "task", "priority": "high", "estimatedHours": 8},
  {"title": "编写测试", "type": "task", "priority": "medium", "estimatedHours": 5}
]

只返回 JSON 数组，不要其他文字，不要嵌套 children。
