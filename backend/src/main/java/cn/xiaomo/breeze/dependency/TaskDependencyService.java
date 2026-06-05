package cn.xiaomo.breeze.dependency;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.dependency.dto.CreateDependencyRequest;
import cn.xiaomo.breeze.dependency.dto.DependencyDTO;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskDependencyService {

    private final TaskDependencyMapper dependencyMapper;
    private final TaskMapper taskMapper;

    @Transactional(rollbackFor = Exception.class)
    public DependencyDTO create(Long taskId, CreateDependencyRequest request) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found");

        Task dependsOn = taskMapper.selectById(request.getDependsOnTaskId());
        if (dependsOn == null) throw new IllegalArgumentException("Depends-on task not found");

        if (taskId.equals(request.getDependsOnTaskId())) {
            throw new IllegalArgumentException("Task cannot depend on itself");
        }

        // 循环检测（仅 blocks / is_blocked_by 类型）
        if ("blocks".equals(request.getType()) || "is_blocked_by".equals(request.getType())) {
            if (wouldCreateCycle(taskId, request.getDependsOnTaskId())) {
                throw new IllegalArgumentException("Adding this dependency would create a circular dependency");
            }
        }

        TaskDependency dep = new TaskDependency();
        dep.setTaskId(taskId);
        dep.setDependsOnTaskId(request.getDependsOnTaskId());
        dep.setType(request.getType());
        dep.setCreatedAt(LocalDateTime.now());
        dependencyMapper.insert(dep);

        DependencyDTO dto = new DependencyDTO();
        dto.setId(dep.getId());
        dto.setTaskId(taskId);
        dto.setDependsOnTaskId(request.getDependsOnTaskId());
        dto.setDependsOnTaskKey(dependsOn.getKey());
        dto.setDependsOnTaskTitle(dependsOn.getTitle());
        dto.setType(request.getType());
        dto.setCreatedAt(dep.getCreatedAt());
        return dto;
    }

    public List<DependencyDTO> listByTask(Long taskId) {
        List<TaskDependency> deps = dependencyMapper.selectList(
            new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getTaskId, taskId));

        if (deps.isEmpty()) return Collections.emptyList();

        Set<Long> taskIds = deps.stream()
            .map(TaskDependency::getDependsOnTaskId).collect(Collectors.toSet());
        Map<Long, Task> taskMap = taskMapper.selectBatchIds(taskIds).stream()
            .collect(Collectors.toMap(Task::getId, t -> t));

        return deps.stream().map(d -> {
            DependencyDTO dto = new DependencyDTO();
            dto.setId(d.getId());
            dto.setTaskId(d.getTaskId());
            dto.setDependsOnTaskId(d.getDependsOnTaskId());
            Task t = taskMap.get(d.getDependsOnTaskId());
            if (t != null) {
                dto.setDependsOnTaskKey(t.getKey());
                dto.setDependsOnTaskTitle(t.getTitle());
            }
            dto.setType(d.getType());
            dto.setCreatedAt(d.getCreatedAt());
            return dto;
        }).toList();
    }

    public void delete(Long taskId, Long dependencyId) {
        TaskDependency dep = dependencyMapper.selectById(dependencyId);
        if (dep == null || !dep.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("Dependency not found");
        }
        dependencyMapper.deleteById(dependencyId);
    }

    private boolean wouldCreateCycle(Long fromTaskId, Long toTaskId) {
        return dfs(toTaskId, fromTaskId, new HashSet<>());
    }

    private boolean dfs(Long current, Long target, Set<Long> visited) {
        if (current.equals(target)) return true;
        if (!visited.add(current)) return false;

        List<TaskDependency> deps = dependencyMapper.selectList(
            new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getTaskId, current)
                .in(TaskDependency::getType, "blocks", "is_blocked_by"));

        for (TaskDependency dep : deps) {
            if (dfs(dep.getDependsOnTaskId(), target, visited)) return true;
        }
        return false;
    }
}
