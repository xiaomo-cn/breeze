package cn.xiaomo.breeze.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.auth.dto.AuthResponse;
import cn.xiaomo.breeze.auth.dto.LoginRequest;
import cn.xiaomo.breeze.auth.dto.LogoutRequest;
import cn.xiaomo.breeze.auth.dto.RefreshRequest;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private Claims claims;
    @Mock
    private Claims newTokenClaims;

    @InjectMocks
    private AuthService authService;

    private static final Long USER_ID = 1L;
    private static final String USERNAME = "testuser";
    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "$2a$10$encoded";
    private static final String ACCESS_TOKEN = "access-token-xxx";
    private static final String REFRESH_TOKEN = "refresh-token-yyy";
    private static final String JTI = "jti-123";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private User createUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setEmail(USERNAME + "@test.com");
        user.setPasswordHash(ENCODED_PASSWORD);
        user.setDisplayName(USERNAME);
        user.setRole(SystemRole.USER.value());
        user.setMustChangePassword(false);
        user.setIsActive(true);
        return user;
    }

    /**
     * 为 buildAuthResponse 设置 mock 链。
     */
    private void mockTokenGeneration() {
        when(jwtUtils.generateAccessToken(any(), eq(USERNAME), any())).thenReturn(ACCESS_TOKEN);
        when(jwtUtils.generateRefreshToken(any(), eq(USERNAME), any())).thenReturn(REFRESH_TOKEN);
        when(jwtUtils.parseToken(REFRESH_TOKEN)).thenReturn(newTokenClaims);
        when(newTokenClaims.getId()).thenReturn(JTI);
        when(jwtUtils.getRefreshTokenTtl()).thenReturn(604800000L);
        when(jwtUtils.getAccessTokenTtl()).thenReturn(3600L);
    }

    @Nested
    class Login {

        @Test
        void shouldLoginSuccessfully() {
            User user = createUser();
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            mockTokenGeneration();

            LoginRequest req = new LoginRequest(USERNAME, RAW_PASSWORD);
            AuthResponse resp = authService.login(req);

            assertThat(resp.userId()).isEqualTo(USER_ID);
            assertThat(resp.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(resp.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(resp.role()).isEqualTo(SystemRole.USER.value());
            assertThat(resp.mustChangePassword()).isFalse();
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            LoginRequest req = new LoginRequest("nobody", RAW_PASSWORD);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid username or password");
        }

        @Test
        void shouldThrowWhenPasswordWrong() {
            User user = createUser();
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("wrongpass", ENCODED_PASSWORD)).thenReturn(false);

            LoginRequest req = new LoginRequest(USERNAME, "wrongpass");

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid username or password");
        }

        @Test
        void shouldThrowWhenAccountDisabled() {
            User user = createUser();
            user.setIsActive(false);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            LoginRequest req = new LoginRequest(USERNAME, RAW_PASSWORD);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Account is disabled");
        }
    }

    @Nested
    class Refresh {

        @Test
        void shouldRefreshSuccessfully() {
            User user = createUser();
            when(jwtUtils.parseToken("old-refresh-token")).thenReturn(claims);
            when(jwtUtils.isRefreshToken(claims)).thenReturn(true);
            when(claims.getId()).thenReturn("old-jti");
            when(claims.getSubject()).thenReturn(USER_ID.toString());
            when(valueOperations.get("refresh_token:old-jti")).thenReturn(USER_ID.toString());
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            mockTokenGeneration();

            RefreshRequest req = new RefreshRequest("old-refresh-token");
            AuthResponse resp = authService.refresh(req);

            assertThat(resp.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(resp.refreshToken()).isEqualTo(REFRESH_TOKEN);

            verify(redisTemplate).delete("refresh_token:old-jti");
        }

        @Test
        void shouldThrowWhenTokenInvalid() {
            when(jwtUtils.parseToken("bad-token")).thenThrow(new RuntimeException("Invalid JWT"));

            RefreshRequest req = new RefreshRequest("bad-token");

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid refresh token");
        }

        @Test
        void shouldThrowWhenTokenIsNotRefreshType() {
            when(jwtUtils.parseToken("access-style-token")).thenReturn(claims);
            when(jwtUtils.isRefreshToken(claims)).thenReturn(false);

            RefreshRequest req = new RefreshRequest("access-style-token");

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Token is not a refresh token");
        }

        @Test
        void shouldThrowWhenTokenRevoked() {
            when(jwtUtils.parseToken("revoked-token")).thenReturn(claims);
            when(jwtUtils.isRefreshToken(claims)).thenReturn(true);
            when(claims.getId()).thenReturn("revoked-jti");
            when(valueOperations.get("refresh_token:revoked-jti")).thenReturn(null);

            RefreshRequest req = new RefreshRequest("revoked-token");

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Refresh token has been revoked");
        }

        @Test
        void shouldThrowWhenUserDisabled() {
            User user = createUser();
            user.setIsActive(false);
            when(jwtUtils.parseToken("valid-token")).thenReturn(claims);
            when(jwtUtils.isRefreshToken(claims)).thenReturn(true);
            when(claims.getId()).thenReturn("jti-ok");
            when(claims.getSubject()).thenReturn(USER_ID.toString());
            when(valueOperations.get("refresh_token:jti-ok")).thenReturn(USER_ID.toString());
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            RefreshRequest req = new RefreshRequest("valid-token");

            assertThatThrownBy(() -> authService.refresh(req))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("User not found or disabled");
        }
    }

    @Nested
    class Logout {

        @Test
        void shouldDeleteRefreshTokenFromRedis() {
            when(jwtUtils.parseToken("token-to-logout")).thenReturn(claims);
            when(jwtUtils.isRefreshToken(claims)).thenReturn(true);
            when(claims.getId()).thenReturn("logout-jti");

            authService.logout(new LogoutRequest("token-to-logout"));

            verify(redisTemplate).delete("refresh_token:logout-jti");
        }

        @Test
        void shouldNotThrowWhenTokenInvalid() {
            when(jwtUtils.parseToken("garbage")).thenThrow(new RuntimeException("bad"));

            authService.logout(new LogoutRequest("garbage"));
        }

        @Test
        void shouldNotDeleteWhenTokenIsAccessType() {
            when(jwtUtils.parseToken("access-token")).thenReturn(claims);
            when(jwtUtils.isRefreshToken(claims)).thenReturn(false);

            authService.logout(new LogoutRequest("access-token"));

            verify(redisTemplate, never()).delete(ArgumentMatchers.<String>any());
        }
    }

    @Nested
    class GetById {

        @Test
        void shouldReturnUser() {
            User user = createUser();
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            User result = authService.getById(USER_ID);

            assertThat(result).isEqualTo(user);
        }
    }
}
