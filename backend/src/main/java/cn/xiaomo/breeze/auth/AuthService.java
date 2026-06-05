package cn.xiaomo.breeze.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.auth.dto.AuthResponse;
import cn.xiaomo.breeze.auth.dto.ChangePasswordRequest;
import cn.xiaomo.breeze.auth.dto.LoginRequest;
import cn.xiaomo.breeze.auth.dto.LogoutRequest;
import cn.xiaomo.breeze.auth.dto.RefreshRequest;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    public AuthResponse login(LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, req.username()));
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        if (!user.getIsActive()) {
            throw new BadCredentialsException("Account is disabled");
        }
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest req) {
        Claims claims;
        try {
            claims = jwtUtils.parseToken(req.refreshToken());
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (!jwtUtils.isRefreshToken(claims)) {
            throw new BadCredentialsException("Token is not a refresh token");
        }

        String jti = claims.getId();
        String redisKey = REFRESH_TOKEN_PREFIX + jti;
        String storedUserId = redisTemplate.opsForValue().get(redisKey);
        if (storedUserId == null) {
            throw new BadCredentialsException("Refresh token has been revoked");
        }

        // Token rotation: invalidate old refresh token
        redisTemplate.delete(redisKey);

        Long userId = Long.parseLong(claims.getSubject());
        User user = userMapper.selectById(userId);
        if (user == null || !user.getIsActive()) {
            throw new BadCredentialsException("User not found or disabled");
        }
        return buildAuthResponse(user);
    }

    public void logout(LogoutRequest req) {
        try {
            Claims claims = jwtUtils.parseToken(req.refreshToken());
            if (jwtUtils.isRefreshToken(claims)) {
                String jti = claims.getId();
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + jti);
            }
        } catch (Exception ignored) {
            // Token already expired or invalid — logout is still successful
        }
    }

    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 修改当前用户密码，验证旧密码后更新为新密码，清除强制改密标记。
     */
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userMapper.updateById(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String role = user.getRole() != null ? user.getRole() : SystemRole.USER.value();
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), role);
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getUsername(), role);

        // Store refresh token in Redis
        Claims claims = jwtUtils.parseToken(refreshToken);
        String jti = claims.getId();
        String redisKey = REFRESH_TOKEN_PREFIX + jti;
        redisTemplate.opsForValue().set(redisKey, user.getId().toString(),
            Duration.ofMillis(jwtUtils.getRefreshTokenTtl()));

        return new AuthResponse(user.getId(), user.getUsername(), role,
            user.getMustChangePassword() != null && user.getMustChangePassword(),
            accessToken, refreshToken, jwtUtils.getAccessTokenTtl());
    }
}
