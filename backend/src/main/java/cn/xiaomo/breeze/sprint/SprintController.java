package cn.xiaomo.breeze.sprint;

import cn.xiaomo.breeze.sprint.dto.BurndownPoint;
import cn.xiaomo.breeze.sprint.dto.CreateSprintRequest;
import cn.xiaomo.breeze.sprint.dto.SprintDTO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @PostMapping
    public ResponseEntity<SprintDTO> create(@PathVariable Long projectId,
                                            @RequestBody CreateSprintRequest request) {
        return ResponseEntity.ok(sprintService.create(projectId, request));
    }

    @GetMapping
    public ResponseEntity<List<SprintDTO>> list(@PathVariable Long projectId) {
        return ResponseEntity.ok(sprintService.listByProject(projectId));
    }

    @GetMapping("/{sprintId}")
    public ResponseEntity<SprintDTO> get(@PathVariable Long projectId,
                                         @PathVariable Long sprintId) {
        return ResponseEntity.ok(sprintService.getById(sprintId));
    }

    @PutMapping("/{sprintId}")
    public ResponseEntity<SprintDTO> update(@PathVariable Long projectId,
                                            @PathVariable Long sprintId,
                                            @RequestBody CreateSprintRequest request) {
        return ResponseEntity.ok(sprintService.update(sprintId, request));
    }

    @PostMapping("/{sprintId}/start")
    public ResponseEntity<SprintDTO> start(@PathVariable Long projectId,
                                           @PathVariable Long sprintId) {
        return ResponseEntity.ok(sprintService.start(sprintId));
    }

    @PostMapping("/{sprintId}/close")
    public ResponseEntity<SprintDTO> close(@PathVariable Long projectId,
                                           @PathVariable Long sprintId) {
        return ResponseEntity.ok(sprintService.close(sprintId));
    }

    @GetMapping("/{sprintId}/burndown")
    public ResponseEntity<List<BurndownPoint>> burndown(@PathVariable Long projectId,
                                                         @PathVariable Long sprintId) {
        return ResponseEntity.ok(sprintService.burndown(sprintId));
    }

    @PostMapping("/{sprintId}/tasks")
    public ResponseEntity<Void> addTask(@PathVariable Long projectId,
                                        @PathVariable Long sprintId,
                                        @RequestBody Map<String, Long> body) {
        sprintService.addTask(sprintId, body.get("taskId"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{sprintId}/tasks/{taskId}")
    public ResponseEntity<Void> removeTask(@PathVariable Long projectId,
                                           @PathVariable Long sprintId,
                                           @PathVariable Long taskId) {
        sprintService.removeTask(sprintId, taskId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{sprintId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId,
                                       @PathVariable Long sprintId) {
        sprintService.delete(sprintId);
        return ResponseEntity.noContent().build();
    }
}
