package cn.xiaomo.breeze.dependency;

import cn.xiaomo.breeze.dependency.dto.CreateDependencyRequest;
import cn.xiaomo.breeze.dependency.dto.DependencyDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/dependencies")
@RequiredArgsConstructor
public class TaskDependencyController {

    private final TaskDependencyService dependencyService;

    @PostMapping
    public ResponseEntity<DependencyDTO> create(@PathVariable Long taskId,
                                                 @RequestBody CreateDependencyRequest request) {
        return ResponseEntity.ok(dependencyService.create(taskId, request));
    }

    @GetMapping
    public ResponseEntity<List<DependencyDTO>> list(@PathVariable Long taskId) {
        return ResponseEntity.ok(dependencyService.listByTask(taskId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long taskId, @PathVariable Long id) {
        dependencyService.delete(taskId, id);
        return ResponseEntity.noContent().build();
    }
}
