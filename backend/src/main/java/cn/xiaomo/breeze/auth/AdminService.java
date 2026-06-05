package cn.xiaomo.breeze.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.xiaomo.breeze.auth.dto.CreateUserRequest;
import cn.xiaomo.breeze.common.PageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 管理员创建用户账号，默认角色为 user，强制首次登录修改密码。
     */
    public User createUser(CreateUserRequest req) {
        if (userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.username()))) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, req.email()))) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName() != null ? req.displayName() : req.username());
        user.setPositionId(req.positionId());
        user.setRole(SystemRole.USER.value());
        user.setMustChangePassword(true);
        user.setIsActive(true);
        userMapper.insert(user);
        return user;
    }

    /**
     * 分页查询所有用户，支持搜索过滤。
     */
    public PageDTO<User> listUsers(int page, int size, String search) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (search != null && !search.isBlank()) {
            wrapper.and(w -> w
                .like(User::getUsername, search)
                .or()
                .like(User::getDisplayName, search)
                .or()
                .like(User::getEmail, search));
        }
        wrapper.orderByAsc(User::getCreatedAt);
        IPage<User> result = userMapper.selectPage(Page.of(page, size), wrapper);
        return PageDTO.of(result.getRecords(), result.getTotal(), page, size);
    }

    /**
     * 修改用户系统角色，至少保留一个 system_admin。
     */
    public User updateUserRole(Long userId, String role) {
        if (!SystemRole.isValid(role)) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        // 防止最后一个管理员被降级
        if (SystemRole.SYSTEM_ADMIN.value().equals(user.getRole())
                && !SystemRole.SYSTEM_ADMIN.value().equals(role)) {
            long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, SystemRole.SYSTEM_ADMIN.value()));
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot demote the last system admin");
            }
        }

        user.setRole(role);
        userMapper.updateById(user);
        return user;
    }

    /**
     * 启停用户账号。
     */
    public User toggleUserStatus(Long userId, boolean isActive) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        user.setIsActive(isActive);
        userMapper.updateById(user);
        return user;
    }

    /**
     * 更新用户资料（显示名、职位描述、职务）。
     */
    public User updateProfile(Long userId, String displayName, String title, Long positionId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (displayName != null) user.setDisplayName(displayName);
        if (title != null) user.setTitle(title);
        user.setPositionId(positionId);
        userMapper.updateById(user);
        return user;
    }

    /**
     * 管理员重置用户密码，强制用户下次登录修改密码。
     */
    public void resetPassword(Long userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        userMapper.updateById(user);
    }
}
