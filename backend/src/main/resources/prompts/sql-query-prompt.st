你是 PostgreSQL 专家。将以下自然语言问题转为 SQL 查询。

问题: $question$
项目ID: $projectId$

数据库表结构:
- tasks(id, project_id, parent_id, key, title, description, type, status, priority,
       assignee_id, reporter_id, sprint_id, story_points, estimated_hours,
       due_date, started_at, resolved_at, risk_level, is_deleted, created_at)
- users(id, username, display_name, email)
- sprints(id, project_id, name, goal, start_date, end_date, status)
- task_dependencies(id, task_id, depends_on_task_id, type)

要求:
1. 只生成 SELECT 查询（只读）
2. 包含 WHERE t.project_id = $projectId$ AND t.is_deleted = false
3. 使用 JOIN 连接用户表和 Sprint 表
4. 限制返回最多 100 行 (LIMIT 100)
5. 只返回 SQL 语句，不要解释

SQL:
