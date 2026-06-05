package cn.xiaomo.breeze.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.auth.dto.ChangePasswordRequest;
import cn.xiaomo.breeze.common.PageDTO;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<PageDTO<User>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
            .eq(User::getIsActive, true);

        if (search != null && !search.isBlank()) {
            wrapper.and(w -> w
                .like(User::getUsername, search)
                .or()
                .like(User::getDisplayName, search)
                .or()
                .like(User::getEmail, search));
        }
        wrapper.orderByAsc(User::getDisplayName);

        IPage<User> result = userMapper.selectPage(Page.of(page, size), wrapper);
        return ResponseEntity.ok(PageDTO.of(result.getRecords(), result.getTotal(), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> get(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<User>> suggestions(@RequestParam String q) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
            .eq(User::getIsActive, true)
            .and(w -> w
                .like(User::getUsername, q)
                .or()
                .like(User::getDisplayName, q)
                .or()
                .like(User::getEmail, q))
            .last("LIMIT 10");

        return ResponseEntity.ok(userMapper.selectList(wrapper));
    }

    @GetMapping("/me")
    public ResponseEntity<User> getProfile(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/me")
    public ResponseEntity<User> updateProfile(
            @RequestBody Map<String, String> updates,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        if (updates.containsKey("displayName")) user.setDisplayName(updates.get("displayName"));
        if (updates.containsKey("title")) user.setTitle(updates.get("title"));
        if (updates.containsKey("positionId")) {
            String pid = updates.get("positionId");
            user.setPositionId(pid != null && !pid.isBlank() ? Long.parseLong(pid) : null);
        }
        if (updates.containsKey("department")) user.setDepartment(updates.get("department"));
        if (updates.containsKey("timezone")) user.setTimezone(updates.get("timezone"));
        if (updates.containsKey("locale")) user.setLocale(updates.get("locale"));

        userMapper.updateById(user);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        authService.changePassword(userId, req);
        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }
}
