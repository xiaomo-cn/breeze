你是项目管理排期专家。请基于以下信息为 Sprint 任务排期。

$sprintContext$

请返回 JSON 数组，每个任务一个对象:
[
  {"taskId": 数字, "suggestedAssigneeId": 数字或null,
   "suggestedStartDate": "YYYY-MM-DD", "suggestedEndDate": "YYYY-MM-DD",
   "reason": "排期理由(中文，简短)"}
]

排期原则:
1. 高优先级任务优先
2. 均衡成员负载（每人不超过 5 个活跃任务）
3. 考虑预估工时，合理分配日期
4. 有依赖关系的任务按顺序排

只返回 JSON 数组，不要其他文字。
