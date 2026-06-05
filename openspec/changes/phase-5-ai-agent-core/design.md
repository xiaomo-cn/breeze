# Phase 5: Design

## Tool Calling Flow (with Confirmation)

```
User message: "帮我把 CASES-42 分配给李四，优先级改为 urgent"
    ↓
AiAgentOrchestrator.streamChat()
    ├─ buildRagContext()       → 检索相关任务 + 用户上下文
    ├─ buildSystemPrompt()     → System prompt + RAG 上下文
    ├─ loadHistory()           → 最近 20 条对话
    └─ ChatClient.prompt()
        .system(...)
        .tools(taskWriteTools)  → Spring AI 生成 function definitions
        .stream()
    ↓
LLM 返回 tool_calls:
  [{ update_task: { taskId: 42, field: "assignee", value: "李四" } },
   { update_task: { taskId: 42, field: "priority", value: "urgent" } }]
    ↓
检测到写工具 → 不执行，通过 SSE 发 tool_confirmation:
  { toolCallId, toolName, params, preview: "将 CASES-42 分配给李四, 优先度 urgent" }
    ↓
用户点击确认 → POST /ai/confirm-tool/{messageId}
    ↓
执行工具 → 写入 DB → 返回结果
    ↓
LLM 基于 tool result 生成最终回复 → SSE 流式推送
```

## Context Window Budget

| 分区 | 预算 | 内容 |
|------|------|------|
| System Prompt | 4K tokens | 角色设定 + 指令 |
| RAG Context | 8K tokens | Layer 1: 项目信息 + 用户任务 (B) |
|  |  | Layer 2: 语义相关任务 (B) |
|  |  | Layer 3: 成员 + 最近活动 |
| History | 16K tokens | 最近 20 条消息 |
| Response | 4K+ tokens | LLM 回复 |

## Conversation Compression

超过 40 条消息时触发压缩：
1. 用 Claude Haiku 对前 20 条生成摘要
2. 摘要作为 system 消息注入
3. 保留最近 20 条完整消息

## Tool Confirmation Rules

| 工具类型 | 操作 | 需要确认 |
|----------|------|---------|
| 读工具 | search_tasks, get_task_detail, list_members, get_sprint_status, get_user_workload | 不需要 |
| 写工具-创建 | create_task, create_subtasks, add_comment | 需要 (可批量确认) |
| 写工具-更新 | update_task, assign_task, add_to_sprint | 需要 |
