package cn.xiaomo.breeze.task;

import cn.xiaomo.breeze.auth.AuthorizationUtils;
import cn.xiaomo.breeze.common.PageDTO;
import cn.xiaomo.breeze.project.ProjectRole;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final AuthorizationUtils authorizationUtils;

    @PostMapping("/projects/{pid}/tasks")
    public ResponseEntity<Task> create(@PathVariable Long pid,
                                       @Valid @RequestBody Task task,
                                       Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authorizationUtils.requireProjectRole(pid, userId, ProjectRole.MEMBER);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(taskService.create(pid, task, userId));
    }

    @GetMapping("/projects/{pid}/tasks")
    public ResponseEntity<PageDTO<Task>> list(
            @PathVariable Long pid,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long sprintId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false, defaultValue = "false") boolean topLevelOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(taskService.listByProject(pid, q, status, priority, type,
            assigneeId, sprintId, parentId, topLevelOnly, page, size, sortBy, sortDir));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> get(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getById(id));
    }

    /** 获取某个任务的直接子任务列表 */
    @GetMapping("/tasks/{id}/children")
    public ResponseEntity<List<Task>> getChildren(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getChildren(id));
    }

    /** 批量获取任务的子任务统计信息 */
    @GetMapping("/projects/{pid}/tasks/subtask-stats")
    public ResponseEntity<Map<Long, Map<String, Integer>>> getSubtaskStats(
            @PathVariable Long pid,
            @RequestParam List<Long> ids) {
        List<Map<String, Object>> rows = taskService.getSubtaskStats(ids);
        Map<Long, Map<String, Integer>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long parentId = ((Number) row.get("parentId")).longValue();
            int total = ((Number) row.get("total")).intValue();
            int done = ((Number) row.get("done")).intValue();
            Map<String, Integer> stats = new HashMap<>();
            stats.put("total", total);
            stats.put("done", done);
            result.put(parentId, stats);
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<Task> update(@PathVariable Long id, @RequestBody Task updates) {
        return ResponseEntity.ok(taskService.update(id, updates));
    }

    @PatchMapping("/tasks/{id}/status")
    public ResponseEntity<Task> updateStatus(@PathVariable Long id,
                                              @RequestBody StatusUpdateRequest req) {
        return ResponseEntity.ok(taskService.updateStatus(id, req.status(), req.sortOrder(), req.kanbanColumnId()));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id,
                                                       Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Task task = taskService.getById(id);
        authorizationUtils.requireProjectRole(task.getProjectId(), userId, ProjectRole.MANAGER);
        taskService.softDelete(id);
        return ResponseEntity.ok(Map.of("message", "Task deleted"));
    }

    /** 获取当前用户的逾期/即将到期任务，按紧迫度分组 */
    @GetMapping("/tasks/my-overdue")
    public ResponseEntity<TaskService.OverdueTasks> myOverdueTasks(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(taskService.getMyOverdueTasks(userId));
    }

    public record StatusUpdateRequest(String status, Integer sortOrder, Long kanbanColumnId) {}
}
