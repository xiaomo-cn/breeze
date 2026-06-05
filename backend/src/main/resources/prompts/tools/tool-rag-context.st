### 项目信息
项目: $projectName$（ID: $projectId$）

$if(semanticTasks)$
### 相关任务（语义搜索）
| 编号 | 标题 | 状态 | 优先级 |
|------|------|------|--------|
$semanticTasks:{t | | $t.key$ | $t.title$ | $t.status$ | $t.priority$ |}$
$endif$

$if(activeSprints)$
### 活跃 Sprint
$activeSprints:{s | - **$s.name$**（$s.dateRange$）
$if(s.goal)$  目标: $s.goal$
$endif$}$
$endif$

$if(myTasks)$
### 你的任务
| 编号 | 标题 | 状态 |
|------|------|------|
$myTasks:{t | | $t.key$ | $t.title$ | $t.status$ |}$
$endif$
