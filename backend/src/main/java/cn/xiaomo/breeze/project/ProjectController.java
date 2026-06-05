package cn.xiaomo.breeze.project;

import cn.xiaomo.breeze.auth.AuthorizationUtils;
import cn.xiaomo.breeze.common.PageDTO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final AuthorizationUtils authorizationUtils;

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Project> create(@RequestBody Project project,
                                          Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(projectService.create(project, userId));
    }

    @GetMapping
    public ResponseEntity<PageDTO<Project>> list(
            Authentication auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(projectService.listByUser(userId, page, size, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> get(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> update(@PathVariable Long id, @RequestBody Project updates) {
        return ResponseEntity.ok(projectService.update(id, updates));
    }

    /** 获取当前用户在该项目中的角色 */
    @GetMapping("/{id}/my-role")
    public ResponseEntity<Map<String, String>> getMyRole(@PathVariable Long id,
                                                          Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ProjectRole role = authorizationUtils.getProjectRole(id, userId);
        return ResponseEntity.ok(Map.of("role", role != null ? role.value() : "non_member"));
    }

    // --- Members ---

    @GetMapping("/{id}/members")
    public ResponseEntity<List<ProjectMember>> listMembers(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.listMembers(id));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ProjectMember> addMember(@PathVariable Long id,
                                                    @RequestBody AddMemberRequest req,
                                                    Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authorizationUtils.requireProjectRole(id, userId, ProjectRole.ADMIN);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(projectService.addMember(id, req.userId(), req.role()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Map<String, String>> removeMember(@PathVariable Long id,
                                                             @PathVariable Long userId,
                                                             Authentication auth) {
        Long currentUserId = (Long) auth.getPrincipal();
        authorizationUtils.requireProjectRole(id, currentUserId, ProjectRole.ADMIN);
        projectService.removeMember(id, userId);
        return ResponseEntity.ok(Map.of("message", "Member removed"));
    }

    @PatchMapping("/{id}/members/{userId}")
    public ResponseEntity<Map<String, String>> updateMemberRole(@PathVariable Long id,
                                                                 @PathVariable Long userId,
                                                                 @RequestBody UpdateRoleRequest req,
                                                                 Authentication auth) {
        Long currentUserId = (Long) auth.getPrincipal();
        authorizationUtils.requireProjectRole(id, currentUserId, ProjectRole.ADMIN);
        projectService.updateMemberRole(id, userId, req.role());
        return ResponseEntity.ok(Map.of("message", "Role updated"));
    }

    public record AddMemberRequest(Long userId, String role) {}
    public record UpdateRoleRequest(String role) {}
}
