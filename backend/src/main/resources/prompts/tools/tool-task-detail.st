### 任务 $key$

| 字段 | 内容 |
|------|------|
| 标题 | $title$ |
| 描述 | $description$ |
| 状态 | $status$ |
| 优先级 | $priority$ |
| 类型 | $type$ |
$if(assignee)$| 指派人 | $assignee$ |
$endif$
$if(dueDate)$| 截止日期 | $dueDate$ |
$endif$
$if(estimatedHours)$| 预估工时 | $estimatedHours$小时 |
$endif$
$if(sprintName)$| 所属 Sprint | $sprintName$ |
$endif$

$if(comments)$
**最近评论：**

| 作者 | 内容 |
|------|------|
$comments:{c | | $c.author$ | $c.content$ |}$
$endif$
