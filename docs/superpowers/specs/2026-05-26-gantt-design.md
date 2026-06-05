# Phase 4-2: 任务依赖 + 甘特图设计

## 范围

实现任务依赖关系管理（CRUD + 循环检测）和项目甘特图（frappe-gantt）。

## 任务依赖

### 后端

| 类 | 包 | 职责 |
|---|---|---|
| `TaskDependency` | `cn.xiaomo.breeze.dependency` | 实体，映射 `task_dependencies` 表 |
| `TaskDependencyMapper` | `cn.xiaomo.breeze.dependency` | MyBatis-Plus BaseMapper |
| `TaskDependencyService` | `cn.xiaomo.breeze.dependency` | CRUD + 循环依赖检测（DFS） |
| `TaskDependencyController` | `cn.xiaomo.breeze.dependency` | REST 端点 |

### API

```
POST   /api/v1/tasks/{taskId}/dependencies      → 创建依赖 { dependsOnTaskId, type }
DELETE /api/v1/tasks/{taskId}/dependencies/{id}  → 删除依赖
GET    /api/v1/tasks/{taskId}/dependencies       → 查询任务依赖列表
```

### 依赖类型

- `blocks` — 阻塞（A blocks B 表示 A 阻塞 B，B 依赖 A 完成）
- `is_blocked_by` — 被阻塞（与 blocks 互逆）
- `relates_to` — 关联（无阻塞关系）

### 循环检测

仅在 `blocks` / `is_blocked_by` 类型时执行。使用 DFS 从目标节点沿依赖链遍历，检测是否会回到起点。检测到循环返回 400。

## 甘特图

### 后端

`GET /api/v1/projects/{pid}/gantt` → 返回 `GanttData`：
```json
{
  "tasks": [{
    "id": 1, "key": "T-1", "title": "...",
    "startDate": "2025-01-01", "endDate": "2025-01-07",
    "assigneeName": "...", "status": "in_progress",
    "dependencies": [2, 3]
  }]
}
```

### 前端

- 安装 `frappe-gantt` npm 包
- `GanttPage`（`/projects/:id/gantt`）：frappe-gantt 渲染，支持拖拽调整日期、查看依赖箭头
- 分组切换（按 Sprint / 按指派人）

## 前端组件

| 文件 | 职责 |
|------|------|
| `pages/GanttPage.tsx` | 甘特图主页面 |
| `components/dependency/DependencyPanel.tsx` | 依赖管理面板，集成到 TaskDetailDrawer |
| `api/dependencies.ts` | 依赖 API 客户端 |
| `api/gantt.ts` | 甘特图 API 客户端 |
| `types/dependency.ts` | 依赖类型定义 |

## 路由

`/projects/:id/gantt` → GanttPage，Sidebar 添加"甘特图"入口。

## 不包含

- 拖拽创建依赖关系（Phase 7 可考虑）
- 甘特图导出（Phase 4-1 已有导出框架，Phase 7 扩展）
