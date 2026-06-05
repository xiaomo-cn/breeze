# Phase 5: Tasks

## AI Agent Core

- [ ] 实现 ContextRetriever：RAG 上下文分层检索（3 层优先级）
- [ ] 实现 AiAgentOrchestrator：编排 ChatClient + Tool Calling + 确认
- [ ] 读取 pgvector 项目级过滤（only search within current project）
- [ ] System Prompt 模板化（项目信息、用户角色、可用工具列表）

## Write Tools

- [ ] update_task（更新任务字段：状态/优先级/指派人/截止日期/预估工时/标题/描述）
- [ ] assign_task（分配任务给指定成员）
- [ ] add_comment（给任务添加 AI 评论，支持 Markdown）
- [ ] create_subtasks（批量创建子任务，用于需求拆解）
- [ ] add_to_sprint（将任务添加到指定 Sprint）

## Read Tools

- [ ] get_task_detail（获取任务完整信息：标题、描述、状态、评论摘要、附件列表）
- [ ] get_sprint_status（Sprint 进度：已完成/总量、燃尽趋势概要）
- [ ] get_user_workload（成员当前负载：各状态任务数量）

## Tool Confirmation

- [ ] 写工具拦截：ToolCallback 中检测写工具 → 暂不执行 → 发 tool_confirmation SSE 事件
- [ ] POST /ai/confirm-tool/{messageId} → 执行待确认的工具调用
- [ ] POST /ai/reject-tool/{messageId} → 取消工具调用
- [ ] 批量确认：多个 tool_call 时一次确认全部
- [ ] 前端：tool_confirmation 卡片 UI（预览操作内容 + 确认/拒绝按钮）

## Context Management

- [ ] Token 计数器（估算：1 token ≈ 4 chars for English, 2 chars for Chinese）
- [ ] 对话压缩：> 40 条 → Haiku 生成摘要 → 注入 system
- [ ] RAG 分层控制：根据可用 Token 动态加载 Layer 1/2/3

## Conversation History

- [ ] GET /ai/conversations（列表，按项目过滤）
- [ ] POST /ai/conversations（新建对话）
- [ ] DELETE /ai/conversations/{id}
- [ ] GET /ai/conversations/{id}/messages
- [ ] 前端：对话历史侧边栏（新建对话、切换历史、删除）

## Rate Limiting

- [ ] 20 条消息/分钟/用户（Redis 滑动窗口）
- [ ] 5 次拆解/小时/用户
- [ ] 100 次 AI 请求/小时/项目
- [ ] 超限返回 429 + retryAfterSeconds

## Verification

- [ ] AI 对话测试：提问项目状态、搜索任务、获取 sprint 进度
- [ ] 工具确认测试：创建任务、更新任务、分配 → 看到确认卡片 → 确认 → 执行成功
- [ ] 工具拒绝测试：点拒绝 → 不执行
- [ ] 上下文压缩测试：长对话 > 40 条 → 正常运作
- [ ] 限流测试：快速发 21 条 → 429
