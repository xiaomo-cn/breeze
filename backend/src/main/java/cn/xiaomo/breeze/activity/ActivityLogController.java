package cn.xiaomo.breeze.activity;

import cn.xiaomo.breeze.common.PageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    @GetMapping("/api/v1/projects/{projectId}/activity")
    public ResponseEntity<PageDTO<ActivityLogDTO>> listByProject(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(activityLogService.listByProject(projectId, page, size));
    }

    @GetMapping("/api/v1/activity")
    public ResponseEntity<PageDTO<ActivityLogDTO>> listForUser(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(activityLogService.listForUser(userId, page, size));
    }
}
