找到 $count$ 个任务：

| 编号 | 标题 | 状态 | 优先级 |
|------|------|------|--------|
$tasks:{t | | $t.key$ | $t.title$ | $t.status$ | $t.priority$ |}$
$if(hasMore)$
（结果可能超过50条，请缩小搜索范围）
$endif$
