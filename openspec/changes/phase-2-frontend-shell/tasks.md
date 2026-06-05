# Phase 2: Tasks

## Project Setup

- [x] 初始化路由（React Router v6）
- [x] 配置 Zustand stores（auth, kanban）
- [x] 配置 Axios instance + request/response interceptors
- [x] JWT 401 拦截 → 自动 refresh → 重试

## Layout + Navigation

- [x] 实现 AppLayout（Sidebar + Header + Content 区域）
- [x] 实现 Sidebar（项目列表导航、当前项目高亮、Settings 子菜单）
- [x] 实现 Header（用户头像、通知 bell 占位、退出、面包屑）

## Pages

- [x] LoginPage / RegisterPage
- [x] DashboardPage（项目卡片、分页、状态筛选、统计摘要、空状态）
- [x] ProjectBoardPage（6列看板、过滤、搜索、Settings 按钮）
- [x] ProjectSettingsPage（通用设置 + 成员管理）
- [ ] ProfilePage（个人设置）— 延后到 Phase 3

## API Layer

- [x] 类型系统：PageDTO<T>、Task、Project、User、ProjectMember
- [x] API 函数：projects（CRUD + 成员管理）、tasks（CRUD + status）、auth（含 logout）、users、search
- [x] listTasks/listProjects 正确解包 PageDTO
- [x] updateTaskStatus 修正 URL 为 PATCH /tasks/{id}/status

## Kanban

- [x] KanbanBoard（@dnd-kit DndContext + DragOverlay + 过滤条）
- [x] KanbanColumn（SortableContext + droppable + WIP 限制指示）
- [x] TaskCard（key、标题、优先级、类型图标、可点击）
- [x] TaskCreateModal（标题、类型、优先级、指派人、截止日期、故事点）
- [x] TaskDetailDrawer（查看/编辑/删除，侧边抽屉不遮挡看板）
- [x] TaskEditForm（全字段编辑表单）
- [x] 拖拽 → API 调用 → 乐观更新 → 失败回滚
- [x] 拖拽时视觉反馈（DragOverlay + drop zone 高亮）

## Search

- [x] SearchBar 组件（全文/语义切换、防抖搜索、结果下拉）
- [x] useSearch hook（300ms 防抖）
- [x] 搜索结果点击跳转到任务详情

## Common Components

- [x] UserSelect 组件（远程搜索用户，可复用）

## Real-time

- [x] usePolling hook（10 秒轮询、Page Visibility 暂停、用户操作后 2s 跳过）
- [x] kanbanStore.diffAndMerge（增量合并，仅变化时更新 UI）
- [ ] SSE 实时事件流 — 延后到 Phase 3（需后端先建 SSE 事件端点 `GET /api/v1/events/stream`）

## AI Chat Panel

- [x] AiChatPanel 浮窗（右下角固定，可展开/收起）
- [x] SSE 流式渲染（fetch + ReadableStream）
- [x] 对话历史显示（用户消息 + AI 回复）
- [x] 建议提示词、新对话按钮、错误重试
- [x] 非项目页禁用输入并提示

## Layout Polish

- [x] Header 面包屑导航
- [x] AppLayout ErrorBoundary 包裹 Outlet
- [x] Sidebar 适配 PageDTO 响应

## Chinese Localization（中文汉化）

- [x] 常量与枚举（TASK_STATUSES、TASK_PRIORITIES、TASK_TYPES、KANBAN_COLUMNS、ROLES 等全程中文标签）
- [x] LoginPage / RegisterPage（登录、注册、用户名、密码、邮箱、错误提示）
- [x] DashboardPage（仪表盘、项目总数、活跃项目、创建项目、分页、空状态）
- [x] ProjectBoardPage / KanbanBoard / KanbanColumn / TaskCard（看板、优先级筛选、类型筛选）
- [x] TaskCreateModal / TaskDetailDrawer / TaskEditForm（创建任务、编辑任务、删除确认、全字段中文）
- [x] ProjectSettingsPage / ProjectEditForm / MemberList / AddMemberModal（设置、基本信息、成员管理）
- [x] SearchBar（搜索任务、全文搜索、语义搜索）
- [x] AiChatPanel（AI 助手、建议提示词、新对话、重试、提示消息）
- [x] Header / Sidebar（退出登录、仪表盘、看板、设置、项目 #）
- [x] AppLayout ErrorBoundary（页面出错了、重试）
- [x] UserSelect 默认 placeholder（负责人）
- [x] `npx tsc --noEmit` 0 错误，`npm run build` 成功（3121 modules）

## Verification

- [ ] 浏览器：登录 → Dashboard → 点击项目 → 看板（需本地启动验证）
- [ ] 拖拽任务从一列到另一列，刷新后状态保持
- [ ] 两个浏览器窗口：一侧操作后另一侧 10s 内轮询更新
- [ ] AiChatPanel：输入指令 → AI 创建任务 → 看板出现卡片（需 DEEPSEEK_API_KEY）
- [ ] 401 自动刷新：手动删除 Access Token → 操作仍正常
