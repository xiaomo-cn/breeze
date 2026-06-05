# Breeze UI 活力现代风重设计

**日期：** 2026-06-03
**状态：** 已确认，待实施
**类型：** UI 视觉重设计

---

## 背景与动机

当前 Breeze 所有页面背景和组件均为白色（`#ffffff`），看板卡片与页面底色无区分，整体视觉层次扁平，缺乏现代 SaaS 产品的精致感。本次改造目标：

- 打破全白页面，增加视觉层次和纵深感
- 引入现代设计元素（毛玻璃、渐变、彩色阴影）
- 统一全站设计语言，建立可维护的色彩体系

## 设计方向

**活力现代风 (Vibrant Modern) — 蓝靛调配色**

| 要素 | 当前 | 改造后 |
|------|------|--------|
| 页面底色 | `#ffffff` | 蓝→青→绿多色微渐变 + 径向光斑装饰 |
| Header | 白色 + 灰边框 | 毛玻璃半透白 `rgba(255,255,255,0.75)` + `backdrop-filter: blur(12px)` |
| Sidebar | 白色 | 半透明 `rgba(255,255,255,0.65)` + `backdrop-filter: blur(8px)` |
| Content 区 | 白色 `token.colorBgContainer` | 透明（继承页面底色） |
| 看板列 | 纯色灰/蓝/绿 | 半透白底 + 列颜色阴影 + 增大圆角 |
| 看板卡片 | AntD 默认白卡 | 白色 + 微阴影 + 增大圆角 |
| 主色 | `#1677ff` 蓝色 | 蓝靛渐变 `#3b82f6 → #6366f1` |

## 色彩体系

### 主色
- **Primary:** `#3b82f6` → `#6366f1`（蓝靛渐变）
- 应用于：主按钮、链接、选中态、品牌标识

### 语义色
| 语义 | 色值 | 用途 |
|------|------|------|
| 危险/紧急 | `#ef4444` | urgent 优先级、删除操作 |
| 警告/高 | `#f59e0b` | high 优先级、逾期提示 |
| 信息/中 | `#3b82f6` | medium 优先级、进行中 |
| 成功/低 | `#10b981` | low 优先级、已完成 |

### 看板列色
| 列 | 色值 | 含义 |
|------|------|------|
| 待办 | `#3b82f6` | 蓝色 |
| 进行中 | `#06b6d4` | 青色 |
| 已完成 | `#10b981` | 绿色 |
| 评审中 | `#f59e0b` | 琥珀色 |
| 测试中 | `#8b5cf6` | 紫罗兰 |

### 中性色（图层体系）
| 层级 | 色值 | 用途 |
|------|------|------|
| 页面底色 | `linear-gradient(135deg, #eef4ff, #f0f5ff, #ecfdf5, #f8fdfc)` | 全局背景 |
| 面板 | `rgba(255,255,255,0.75)` + `backdrop-filter: blur(4px)` | 看板列、卡片容器 |
| 侧栏 | `rgba(255,255,255,0.65)` + `backdrop-filter: blur(8px)` | Sidebar |
| 顶栏 | `rgba(255,255,255,0.75)` + `backdrop-filter: blur(12px)` | Header |
| 内容卡片 | `#ffffff` | 任务卡片、表单卡片 |

## 组件改造规范

### 通用规则
所有 Ant Design 组件通过 `ConfigProvider` theme token 统一控制：

- `colorPrimary`: `#3b82f6`
- `borderRadiusLG`: `12px`（增大圆角）
- `colorBgContainer`: 保持白色用于卡片内部
- `colorBorderSecondary`: 半透明蓝色调

### 各组件调整

| 组件 | 改造内容 |
|------|----------|
| **Card** | 白色底 + `box-shadow: 0 2px 12px rgba(59,130,246,0.06)` + 半透明边框 + 圆角 12px |
| **Button (primary)** | `background: linear-gradient(135deg, #3b82f6, #6366f1)` + 彩色阴影 |
| **Input/Select** | 边框 `rgba(59,130,246,0.15)` + focus 时蓝色发光 |
| **Table** | 表头微阴影 + 细边框 |
| **Modal/Drawer** | 大阴影 + 可选半透白背景 |
| **Tag/Badge** | 语义色映射，保持 AntD 默认行为 |
| **Progress** | 渐变色填充 + 圆角 |

## 涉及文件清单

### 第 1 步：主题层（2 文件）
- `frontend/src/App.tsx` — ConfigProvider theme token 扩展
- `frontend/src/styles/index.css` — 页面渐变背景 + CSS 变量

### 第 2 步：布局框架（3 文件）
- `frontend/src/components/layout/AppLayout.tsx` — Content 背景透明化
- `frontend/src/components/layout/Header.tsx` — 毛玻璃效果 + 渐变 Logo
- `frontend/src/components/layout/Sidebar.tsx` — 半透明 + 选中高亮

### 第 3 步：组件与页面（约 8 文件）
- `frontend/src/components/kanban/TaskCard.tsx` — 卡片圆角 + 阴影
- `frontend/src/components/kanban/KanbanBoard.tsx` — 列容器样式
- `frontend/src/pages/DashboardPage.tsx` — 统计卡片 + 项目列表卡片
- `frontend/src/pages/SprintPage.tsx` — Sprint 卡片样式
- `frontend/src/pages/SprintDetailPage.tsx` — 内嵌看板 + 燃尽图卡片
- `frontend/src/pages/ProjectSettingsPage.tsx` — 设置表单卡片
- `frontend/src/pages/GanttPage.tsx` — 甘特图容器
- `frontend/src/pages/ReportPage.tsx` — 报表卡片
- `frontend/src/pages/ProfilePage.tsx` — 个人设置卡片
- `frontend/src/pages/LoginPage.tsx` — 登录卡片（已使用 bg-gray-50，可保留）
- `frontend/src/pages/RegisterPage.tsx` — 注册卡片（同上）
- `frontend/src/constants/index.ts` — 优先级颜色常量微调

## 不改动的内容

- 业务逻辑代码（store、API、hooks）
- 路由结构
- 拖拽逻辑（dnd-kit）
- 响应式断点
- 甘特图库 frappe-gantt CSS（仅容器背景适配）
- 登录/注册页的 Tailwind `bg-gray-50` 布局

## 验证方式

1. **视觉检查**：启动前端 `npm run dev`，逐一访问各页面确认风格一致
2. **看板功能**：拖拽卡片、创建任务、切换列，确认交互正常
3. **响应式**：缩放浏览器窗口，确认移动端/平板布局无破损
4. **组件覆盖**：打开 Modal、Drawer、Dropdown、Popover，确认无样式冲突
5. **浏览器兼容**：`backdrop-filter` 在现代浏览器中普遍支持（Chrome 76+, Edge 79+, Safari 9+）
