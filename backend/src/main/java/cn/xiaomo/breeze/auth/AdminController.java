package cn.xiaomo.breeze.auth;

import cn.xiaomo.breeze.auth.dto.CreatePositionRequest;
import cn.xiaomo.breeze.auth.dto.CreateUserRequest;
import cn.xiaomo.breeze.auth.dto.ResetPasswordRequest;
import cn.xiaomo.breeze.auth.dto.ToggleStatusRequest;
import cn.xiaomo.breeze.auth.dto.UpdatePositionRequest;
import cn.xiaomo.breeze.auth.dto.UpdateProfileRequest;
import cn.xiaomo.breeze.auth.dto.UpdateRoleRequest;
import cn.xiaomo.breeze.common.PageDTO;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final PositionService positionService;

    @PostMapping("/users")
    public ResponseEntity<User> createUser(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createUser(req));
    }

    @GetMapping("/users")
    public ResponseEntity<PageDTO<User>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(adminService.listUsers(page, size, search));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<User> updateUserRole(@PathVariable Long id,
                                                @RequestBody UpdateRoleRequest req) {
        return ResponseEntity.ok(adminService.updateUserRole(id, req.role()));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<User> toggleUserStatus(@PathVariable Long id,
                                                  @RequestBody ToggleStatusRequest req) {
        return ResponseEntity.ok(adminService.toggleUserStatus(id, req.isActive()));
    }

    @PatchMapping("/users/{id}/profile")
    public ResponseEntity<User> updateProfile(@PathVariable Long id,
                                               @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(adminService.updateProfile(id, req.displayName(), req.title(), req.positionId()));
    }

    @PatchMapping("/users/{id}/password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable Long id,
                                                              @Valid @RequestBody ResetPasswordRequest req) {
        adminService.resetPassword(id, req.newPassword());
        return ResponseEntity.ok(Map.of("message", "密码已重置"));
    }

    // ==================== 职务管理（仅系统管理员） ====================

    @GetMapping("/positions")
    public ResponseEntity<List<Position>> listPositions() {
        return ResponseEntity.ok(positionService.listAll());
    }

    @PostMapping("/positions")
    public ResponseEntity<Position> createPosition(@Valid @RequestBody CreatePositionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(positionService.create(req.name(), req.color()));
    }

    @PatchMapping("/positions/{id}")
    public ResponseEntity<Position> updatePosition(@PathVariable Long id,
                                                    @RequestBody UpdatePositionRequest req) {
        return ResponseEntity.ok(positionService.update(id, req.name(), req.color()));
    }

    @DeleteMapping("/positions/{id}")
    public ResponseEntity<Map<String, String>> deletePosition(@PathVariable Long id) {
        positionService.delete(id);
        return ResponseEntity.ok(Map.of("message", "职务已删除"));
    }
}
