package cn.xiaomo.breeze.gantt;

import cn.xiaomo.breeze.dependency.TaskDependency;
import cn.xiaomo.breeze.dependency.TaskDependencyMapper;
import cn.xiaomo.breeze.gantt.dto.GanttData;
import cn.xiaomo.breeze.gantt.dto.GanttData.GanttTask;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class GanttController {

    private final TaskMapper taskMapper;
    private final TaskDependencyMapper dependencyMapper;

    @GetMapping("/gantt")
    public ResponseEntity<GanttData> gantt(@PathVariable Long projectId) {
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getIsDeleted, false)
                .orderByAsc(Task::getCreatedAt));

        Set<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());

        Map<Long, List<Long>> depMap = new HashMap<>();
        if (!taskIds.isEmpty()) {
            List<TaskDependency> deps = dependencyMapper.selectList(
                new LambdaQueryWrapper<TaskDependency>()
                    .in(TaskDependency::getTaskId, taskIds));
            for (TaskDependency d : deps) {
                depMap.computeIfAbsent(d.getTaskId(), k -> new ArrayList<>())
                    .add(d.getDependsOnTaskId());
            }
        }

        List<GanttTask> ganttTasks = tasks.stream().map(t -> {
            GanttTask gt = new GanttTask();
            gt.setId(t.getId());
            gt.setKey(t.getKey());
            gt.setTitle(t.getTitle());
            gt.setStartDate(t.getStartedAt() != null ? t.getStartedAt().toLocalDate().toString() : null);
            gt.setEndDate(t.getResolvedAt() != null ? t.getResolvedAt().toLocalDate().toString() : null);
            gt.setStatus(t.getStatus());
            gt.setDependencies(depMap.getOrDefault(t.getId(), Collections.emptyList()));
            return gt;
        }).collect(Collectors.toList());

        GanttData data = new GanttData();
        data.setTasks(ganttTasks);
        return ResponseEntity.ok(data);
    }
}
