# Phase 2: Design

## Frontend Architecture

```
src/
├── main.tsx                    # Entry
├── App.tsx                     # Router + Providers
├── api/
│   ├── client.ts              # Axios instance + interceptors
│   ├── auth.ts                # login, register, refresh
│   ├── projects.ts            # CRUD
│   ├── tasks.ts               # CRUD + move
│   └── ai.ts                  # chat (SSE stream)
├── stores/
│   ├── authStore.ts           # user, tokens
│   ├── projectStore.ts        # projects list
│   ├── kanbanStore.ts         # columns, tasks, drag state
│   └── uiStore.ts             # sidebar, modals, theme
├── hooks/
│   ├── useSse.ts              # fetch + ReadableStream（AI 对话）
│   ├── useRealtimeEvents.ts   # SSE 实时事件（看板/通知）
│   └── useAuth.ts             # auth guard
├── components/
│   ├── layout/
│   │   ├── AppLayout.tsx      # Sidebar + Header + Content
│   │   ├── Sidebar.tsx
│   │   └── Header.tsx
│   ├── kanban/
│   │   ├── KanbanBoard.tsx    # @dnd-kit 容器
│   │   ├── KanbanColumn.tsx   # 列 (droppable)
│   │   ├── TaskCard.tsx       # 任务卡片 (draggable)
│   │   └── TaskCreateModal.tsx
│   ├── task/
│   │   └── TaskDetailModal.tsx
│   ├── ai/
│   │   └── AiChatPanel.tsx    # 浮窗 + 对话
│   └── common/
│       ├── Loading.tsx
│       └── ErrorBoundary.tsx
├── pages/
│   ├── LoginPage.tsx
│   ├── RegisterPage.tsx
│   ├── DashboardPage.tsx
│   ├── ProjectBoardPage.tsx
│   └── ProfilePage.tsx
└── routes/
    └── index.tsx              # React Router config
```

## State Management (Zustand)

**kanbanStore** — 乐观更新策略：

```
拖拽 Task A 从 Column 1 → Column 2
    1. 立即更新本地 state (乐观更新)
    2. PATCH /api/v1/tasks/{id}/status { status, sortOrder }
    3. SSE 广播给其他用户 → 其他用户更新 state
    4. 如果 API 失败 → 回滚本地 state
```

## SSE 实时事件

```
端点: GET /api/v1/events/stream?projectId={id}
协议: SSE（Server-Sent Events），fetch + ReadableStream

事件类型（通过 event: 字段区分）:
  event: task_moved
  data: {"taskId":42,"newStatus":"IN_PROGRESS","sortOrder":3,"userId":7}

  event: task_updated
  data: {"taskId":42,"changes":{"title":"新标题","priority":"HIGH"}}

  event: comment_added
  data: {"commentId":99,"taskId":42,"author":"张三"}

  event: notification
  data: {"type":"ASSIGNED","taskTitle":"修复登录bug","projectId":1}

设计决策:
  - 选 SSE 而非 WebSocket：所有写入走 REST API，SSE 只需处理服务端→客户端推送
  - 单端点多事件类型：复用现有 fetch+ReadableStream 模式（与 AI Chat 一致）
  - 生产环境 HTTP/2 下无连接数限制，无需多路复用层（STOMP/SockJS）
```

## Routing

```
/login, /register          → Auth pages (no layout)
/                          → Dashboard
/projects/:key             → Kanban (default view)
/projects/:key/tasks/:id   → Task detail (modal overlay)
/ai                        → AI assistant fullscreen
/profile                   → User profile
```
