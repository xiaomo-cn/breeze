# Breeze UI 活力现代风重设计 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Breeze 前端从全白扁平风格改造为活力现代风（蓝靛调），引入渐变背景、毛玻璃效果、彩色阴影，建立统一的视觉层次体系。

**Architecture:** 分 3 阶段实施 — 先改全局主题层（AntD token + CSS 变量），再改布局框架（Header/Sidebar/Content），最后逐个改造组件和页面。每阶段改动不依赖后续阶段，独立生效。

**Tech Stack:** React 18 + TypeScript、Ant Design 5（ConfigProvider theme token）、Tailwind CSS 3、内联 style

---

### Task 1: 全局主题层 — App.tsx + index.css

**Files:**
- Modify: `frontend/src/App.tsx`（ConfigProvider theme token 扩展）
- Modify: `frontend/src/styles/index.css`（页面渐变背景 + body 样式）

- [ ] **Step 1: 扩展 ConfigProvider theme token**

在 `frontend/src/App.tsx` 第 16 行，将单一的 `colorPrimary` 替换为完整的 token 配置：

```tsx
// 替换第 16 行：
// 旧: <ConfigProvider locale={zhCN} theme={{ token: { colorPrimary: '#1677ff' } }}>
// 新:
<ConfigProvider
  locale={zhCN}
  theme={{
    token: {
      colorPrimary: '#3b82f6',
      colorInfo: '#3b82f6',
      colorSuccess: '#10b981',
      colorWarning: '#f59e0b',
      colorError: '#ef4444',
      borderRadiusLG: 12,
      borderRadius: 8,
      colorBorderSecondary: 'rgba(59,130,246,0.1)',
      colorBgContainer: '#ffffff',
      boxShadow: '0 2px 12px rgba(59,130,246,0.06)',
      boxShadowSecondary: '0 4px 16px rgba(59,130,246,0.1)',
    },
  }}
>
```

- [ ] **Step 2: 添加全局 CSS 渐变背景和 body 样式**

在 `frontend/src/styles/index.css` 的 body 块（第 5-8 行）中扩展背景样式：

```css
/* 替换第 5-8 行的 body 块： */
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background: linear-gradient(135deg, #eef4ff 0%, #f0f5ff 30%, #ecfdf5 60%, #f8fdfc 100%);
  background-attachment: fixed;
  min-height: 100vh;
}

/* 在 body 之后添加装饰光斑（通过伪元素实现） */
#root {
  position: relative;
}

#root::before {
  content: '';
  position: fixed;
  top: -100px;
  right: -80px;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(59,130,246,0.08), transparent 70%);
  border-radius: 50%;
  pointer-events: none;
  z-index: 0;
}

#root::after {
  content: '';
  position: fixed;
  bottom: -120px;
  left: -60px;
  width: 350px;
  height: 350px;
  background: radial-gradient(circle, rgba(6,182,212,0.05), transparent 70%);
  border-radius: 50%;
  pointer-events: none;
  z-index: 0;
}
```

- [ ] **Step 3: 验证**

启动前端查看效果：
```bash
cd frontend && npm run dev
```
浏览器打开 `http://localhost:5173`：登录后应看到页面背景从全白变为蓝青微渐变，AntD 组件主色变为蓝色 `#3b82f6`。

---

### Task 2: 布局框架 — AppLayout、Header、Sidebar

**Files:**
- Modify: `frontend/src/components/layout/AppLayout.tsx`（Content 去白底）
- Modify: `frontend/src/components/layout/Header.tsx`（毛玻璃 + 渐变 Logo）
- Modify: `frontend/src/components/layout/Sidebar.tsx`（半透明 + 选中高亮）

- [ ] **Step 1: AppLayout — Content 背景透明化**

在 `frontend/src/components/layout/AppLayout.tsx`，将 Content style 的 `background` 从 `token.colorBgContainer` 改为 `transparent`，并增加 `position: relative; z-index: 1`：

```tsx
// 替换第 97-107 行 Content 的 style：
<Content
  className="app-content"
  style={{
    margin: 16,
    padding: 24,
    background: 'transparent',     // 原: token.colorBgContainer
    borderRadius: token.borderRadiusLG,
    minHeight: 280,
    overflow: 'hidden',
    position: 'relative',
    zIndex: 1,
  }}
>
```

同时给最外层 `Layout` 添加透明背景（当前无显式背景，添加以确保）：

```tsx
// 第 93 行，给 Layout 加 style：
<Layout style={{ minHeight: '100vh', background: 'transparent' }}>
```

- [ ] **Step 2: Header — 毛玻璃效果 + 渐变 Logo**

在 `frontend/src/components/layout/Header.tsx` 第 60-68 行，替换 AntHeader 的 style：

```tsx
// 替换 AntHeader 的 style：
<AntHeader
  style={{
    background: 'rgba(255,255,255,0.75)',
    backdropFilter: 'blur(12px)',
    WebkitBackdropFilter: 'blur(12px)',
    padding: '0 16px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottom: '1px solid rgba(59,130,246,0.12)',
    position: 'relative',
    zIndex: 10,
  }}
>
```

将 Logo 文字（第 71-73 行）改为渐变色：

```tsx
// 替换第 71-73 行的 Logo span：
<span style={{
  fontWeight: 800,
  fontSize: 16,
  whiteSpace: 'nowrap',
  background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
  WebkitBackgroundClip: 'text',
  WebkitTextFillColor: 'transparent',
  backgroundClip: 'text',
}}>
  Breeze
</span>
```

- [ ] **Step 3: Sidebar — 半透明 + 选中项渐变**

在 `frontend/src/components/layout/Sidebar.tsx` 第 114 行，替换 Sider 的 background：

```tsx
// 替换 Sider 的 style：
<Sider
  collapsible
  collapsed={collapsed}
  onCollapse={onCollapse}
  trigger={null}
  breakpoint="lg"
  collapsedWidth={0}
  width={220}
  style={{
    background: 'rgba(255,255,255,0.65)',
    backdropFilter: 'blur(8px)',
    WebkitBackdropFilter: 'blur(8px)',
    borderRight: '1px solid rgba(59,130,246,0.08)',
  }}
>
```

Menu 组件也改为透明背景，并添加选中态样式。在同文件 `Sider` 内，给 `Menu` 添加 style：

```tsx
<Menu
  mode="inline"
  selectedKeys={getSelectedKeys()}
  defaultOpenKeys={getOpenKeys()}
  items={items}
  onClick={({ key }) => navigate(key)}
  style={{
    background: 'transparent',
    borderInlineEnd: 'none',
  }}
/>
```

- [ ] **Step 4: 验证**

刷新浏览器，确认：Header 变为毛玻璃半透效果、品牌名变为蓝靛渐变文字、Sidebar 变为半透背景、Content 区域去掉了白色底，继承页面渐变背景。

---

### Task 3: 看板组件 — TaskCard + KanbanBoard

**Files:**
- Modify: `frontend/src/components/kanban/TaskCard.tsx`（卡片圆角 + 阴影）
- Modify: `frontend/src/components/kanban/KanbanBoard.tsx`（列容器样式）

- [ ] **Step 1: TaskCard — 卡片样式升级**

在 `frontend/src/components/kanban/TaskCard.tsx` 第 44 行，给 `<Card>` 添加 style 和 bodyStyle props：

```tsx
// 替换第 44 行 <Card size="small" hoverable> 为：
<Card
  size="small"
  hoverable
  style={{
    borderRadius: 10,
    boxShadow: '0 2px 8px rgba(59,130,246,0.06)',
    border: '1px solid rgba(59,130,246,0.06)',
    marginBottom: 8,
  }}
  bodyStyle={{ padding: '10px 12px' }}
>
```

将 TYPE_ICONS 中的 task 图标颜色改为新主色（第 12 行）：

```tsx
task: <CheckSquareOutlined style={{ color: '#3b82f6' }} />,
// 原: style={{ color: '#1677ff' }}
```

将子任务已完成徽章颜色改为新绿色（第 63-64 行）：

```tsx
// 第 63 行：
color: task.subtaskStats.done === task.subtaskStats.total ? '#10b981' : '#3b82f6',
// 第 64 行：
background: task.subtaskStats.done === task.subtaskStats.total ? '#ecfdf5' : '#eff6ff',
```

- [ ] **Step 2: KanbanBoard — 列容器样式升级**

在 `frontend/src/components/kanban/KanbanBoard.tsx` 第 175-184 行，替换列容器的 style：

```tsx
// 替换第 175-184 行列容器的 style：
<div
  key={col.id}
  style={{
    flex: 1,
    minWidth: 260,
    background: 'rgba(255,255,255,0.75)',
    backdropFilter: 'blur(4px)',
    WebkitBackdropFilter: 'blur(4px)',
    borderRadius: 12,
    padding: 12,
    borderTop: `3px solid ${col.color || '#3b82f6'}`,
    border: `1px solid rgba(59,130,246,0.08)`,
    boxShadow: `0 4px 16px ${(col.color || '#3b82f6')}14`,
  }}
>
```

- [ ] **Step 3: 验证**

导航到看板页面，确认：卡片有微阴影和更大圆角、类型图标和子任务颜色已更新、列容器有毛玻璃和彩色阴影效果。

---

### Task 4: Dashboard 仪表盘页面

**Files:**
- Modify: `frontend/src/pages/DashboardPage.tsx`

- [ ] **Step 1: Dashboard 统计卡片和项目卡片样式升级**

在 `frontend/src/pages/DashboardPage.tsx` 中：

**统计卡片** — 第 66-74 行的 Statistic Card 改用半透白底：

```tsx
// 第 66 行的 Card：
<Card
  style={{
    background: 'rgba(255,255,255,0.8)',
    backdropFilter: 'blur(4px)',
    borderRadius: 12,
    boxShadow: '0 2px 12px rgba(59,130,246,0.06)',
    border: '1px solid rgba(59,130,246,0.06)',
  }}
>
// 第 71 行的 Card 同样添加上述 style
```

**项目卡片** — 第 106-109 行的 Card 添加 style：

```tsx
<Card
  hoverable
  onClick={() => navigate(`/projects/${p.id}`)}
  style={{
    background: 'rgba(255,255,255,0.8)',
    borderRadius: 10,
    boxShadow: '0 1px 6px rgba(59,130,246,0.06)',
    border: '1px solid rgba(59,130,246,0.05)',
  }}
>
```

项目图标颜色改为新主色（第 111 行）：

```tsx
<ProjectOutlined style={{ fontSize: 24, color: '#3b82f6' }} />
// 原: color: '#1677ff'
```

**活动卡片和风险卡片** — 第 134-139 行、第 141-145 行的 Card 添加 style：

```tsx
<Card
  title={...}
  style={{
    marginTop: 24,
    background: 'rgba(255,255,255,0.8)',
    borderRadius: 10,
    boxShadow: '0 2px 12px rgba(59,130,246,0.06)',
    border: '1px solid rgba(59,130,246,0.06)',
  }}
>
```

- [ ] **Step 2: 验证**

导航到仪表盘页面，确认统计卡片、项目列表卡片、活动卡片都有半透白底 + 微阴影。

---

### Task 5: Sprint 页面

**Files:**
- Modify: `frontend/src/pages/SprintPage.tsx`
- Modify: `frontend/src/pages/SprintDetailPage.tsx`

- [ ] **Step 1: SprintPage — Sprint 卡片样式**

在 `frontend/src/pages/SprintPage.tsx` 第 83-88 行的 Card 添加 style：

```tsx
<Card
  key={s.id}
  hoverable
  onClick={() => navigate(`/projects/${projectId}/sprints/${s.id}`)}
  style={{
    marginBottom: 12,
    background: 'rgba(255,255,255,0.8)',
    borderRadius: 10,
    boxShadow: '0 2px 10px rgba(59,130,246,0.06)',
    border: '1px solid rgba(59,130,246,0.06)',
  }}
>
```

- [ ] **Step 2: SprintDetailPage — 燃尽图卡片**

在 `frontend/src/pages/SprintDetailPage.tsx` 第 193 行，燃尽图 Card 添加 style：

```tsx
<Card
  title="燃尽图"
  style={{
    marginTop: 16,
    background: 'rgba(255,255,255,0.8)',
    borderRadius: 10,
    boxShadow: '0 2px 12px rgba(59,130,246,0.06)',
    border: '1px solid rgba(59,130,246,0.06)',
  }}
>
```

- [ ] **Step 3: 验证**

导航到 Sprint 列表和 Sprint 详情页面，确认卡片风格一致。

---

### Task 6: 设置页面 + 报表页面 + 甘特图 + 个人设置

**Files:**
- Modify: `frontend/src/pages/ProjectSettingsPage.tsx`
- Modify: `frontend/src/pages/ReportPage.tsx`
- Modify: `frontend/src/pages/GanttPage.tsx`
- Modify: `frontend/src/pages/ProfilePage.tsx`

- [ ] **Step 1: ProjectSettingsPage — Tabs 容器**

在 `frontend/src/pages/ProjectSettingsPage.tsx` 第 37-54 行，给外层 div 和内嵌 Card（在 ProjectEditForm 中）添加背景。不需要改外层 div，因为 Tabs 自身不设置背景。

Tabs 下的子组件（ProjectEditForm、MemberList、BoardSettings）使用 AntD Card，它们会自动继承 ConfigProvider 的圆角和阴影增强。此文件无需代码改动，仅验证确认。

- [ ] **Step 2: ReportPage — 报表容器卡片**

在 `frontend/src/pages/ReportPage.tsx` 第 93 行的 Card 添加 style：

```tsx
<Card
  title="报表中心"
  style={{
    background: 'rgba(255,255,255,0.8)',
    borderRadius: 12,
    boxShadow: '0 2px 12px rgba(59,130,246,0.06)',
    border: '1px solid rgba(59,130,246,0.06)',
  }}
  extra={...}
>
```

AI 报告模态框内预览区（第 175-178 行）也需要背景适配：

```tsx
<div style={{
  maxHeight: '60vh', overflow: 'auto', padding: 16,
  background: '#f8fafc',
  border: '1px solid rgba(59,130,246,0.1)',
  borderRadius: 8,
  whiteSpace: 'pre-wrap', fontFamily: 'monospace', fontSize: 13
}}>
```

- [ ] **Step 3: GanttPage — 甘特图容器**

在 `frontend/src/pages/GanttPage.tsx` 第 55 行外层 div，将 `padding: 24` 保留，无需额外修改（甘特图使用 frappe-gantt 库自身的样式）。仅在外层添加一个包裹 Card：

```tsx
// 第 55 行，将整个内容包裹在一个半透背景容器中：
<div style={{ padding: 24 }}>
  <div style={{
    background: 'rgba(255,255,255,0.8)',
    borderRadius: 12,
    padding: 20,
    boxShadow: '0 2px 12px rgba(59,130,246,0.06)',
    border: '1px solid rgba(59,130,246,0.06)',
  }}>
    {/* 原有 Segmented + Spin + ganttRef 内容（第 56-71 行） */}
  </div>
</div>
```

- [ ] **Step 4: ProfilePage — 个人设置卡片**

在 `frontend/src/pages/ProfilePage.tsx` 第 58 行的 Card 添加 style：

```tsx
<Card
  title="个人设置"
  style={{
    background: 'rgba(255,255,255,0.8)',
    borderRadius: 12,
    boxShadow: '0 2px 12px rgba(59,130,246,0.06)',
    border: '1px solid rgba(59,130,246,0.06)',
  }}
>
```

- [ ] **Step 5: 验证**

逐一访问各页面：项目设置、报表中心、甘特图、个人设置。确认每个页面的主容器卡片都有半透白底+微阴影+12px圆角。

---

### Task 7: 常量文件 + 登录/注册页

**Files:**
- Modify: `frontend/src/constants/index.ts`（优先级颜色微调）
- Modify: `frontend/src/pages/LoginPage.tsx`（登录卡片适配）
- Modify: `frontend/src/pages/RegisterPage.tsx`（注册卡片适配）

- [ ] **Step 1: constants/index.ts — 优先级颜色对齐新语义色**

在 `frontend/src/constants/index.ts` 中，`PRIORITY_COLORS` 的值已是 AntD Tag 的语义色名（`red`, `orange`, `blue`, `green`），ConfigProvider 的 token 已将这些映射到新色值，无需改动。

但 `TASK_PRIORITIES` 中的 `color` 字段（第 2-6 行）如果用作内联样式引用，需要确认。当前代码使用 `PRIORITY_COLORS` 作为 Tag color prop，颜色名称不变，AntD 会自动解析——无需改动。

- [ ] **Step 2: LoginPage — 登录卡片适配**

在 `frontend/src/pages/LoginPage.tsx` 中：

第 30 行，将外层 div 的 `bg-gray-50` 改为 `bg-transparent`：

```tsx
// 替换第 30 行：
<div className="flex items-center justify-center min-h-screen bg-transparent">
```

第 31 行，给 Card 添加 style：

```tsx
<Card
  style={{
    width: 400,
    maxWidth: 'calc(100vw - 32px)',
    borderRadius: 12,
    boxShadow: '0 4px 24px rgba(59,130,246,0.12)',
    border: '1px solid rgba(59,130,246,0.08)',
  }}
>
```

第 32 行，Title 品牌名改为渐变色：

```tsx
<Title level={3} style={{
  textAlign: 'center',
  marginBottom: 24,
  background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
  WebkitBackgroundClip: 'text',
  WebkitTextFillColor: 'transparent',
  backgroundClip: 'text',
}}>
  Breeze
</Title>
```

- [ ] **Step 3: RegisterPage — 同 LoginPage 处理**

第 33 行，外层 div：

```tsx
<div className="flex items-center justify-center min-h-screen bg-transparent">
```

第 34 行，Card：

```tsx
<Card
  style={{
    width: 400,
    maxWidth: 'calc(100vw - 32px)',
    borderRadius: 12,
    boxShadow: '0 4px 24px rgba(59,130,246,0.12)',
    border: '1px solid rgba(59,130,246,0.08)',
  }}
>
```

- [ ] **Step 4: 验证**

访问登录页和注册页，确认背景渐变可见，卡片风格与全站一致。

---

### Task 8: 最终验证

- [ ] **Step 1: 全页面走查**

启动前端，逐一访问所有页面：
1. `/login` — 登录页
2. `/register` — 注册页
3. `/` — 仪表盘
4. `/projects/:id` — 看板页
5. `/projects/:id/sprints` — Sprint 列表
6. `/projects/:id/sprints/:sid` — Sprint 详情
7. `/projects/:id/reports` — 报表中心
8. `/projects/:id/gantt` — 甘特图
9. `/projects/:id/settings` — 项目设置
10. `/profile` — 个人设置

每页确认：
- 页面背景为蓝青微渐变
- Header 为毛玻璃半透效果
- Sidebar 为半透效果
- 主内容区卡片为半透白底 + 12px 圆角 + 微阴影
- 主色为蓝色 `#3b82f6`

- [ ] **Step 2: 交互验证**

- 看板拖拽卡片：确认拖拽动画和样式正常
- 打开 Modal / Drawer：确认无样式冲突
- 打开 Dropdown / Popover：确认无样式冲突
- 缩放浏览器窗口到 768px / 576px：确认响应式无破损

- [ ] **Step 3: TypeScript 类型检查**

```bash
cd frontend && npx tsc --noEmit
```
预期：无新增类型错误。

---
