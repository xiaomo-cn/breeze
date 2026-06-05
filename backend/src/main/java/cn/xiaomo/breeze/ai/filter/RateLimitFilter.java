package cn.xiaomo.breeze.ai.filter;

import cn.xiaomo.breeze.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_AI_PER_MINUTE = 20;
    private static final int MAX_LOGIN_PER_MINUTE = 5;
    private static final int MAX_REGISTER_PER_MINUTE = 3;
    private static final String KEY_PREFIX_AI = "ratelimit:ai:";
    private static final String KEY_PREFIX_AUTH = "ratelimit:auth:";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        int maxRequests;
        String keyPrefix;
        String identifier;

        if (path.startsWith("/api/v1/ai/")) {
            Long userId = getUserId(request);
            if (userId == null) {
                chain.doFilter(request, response);
                return;
            }
            maxRequests = MAX_AI_PER_MINUTE;
            keyPrefix = KEY_PREFIX_AI;
            identifier = String.valueOf(userId);
        } else if (path.equals("/api/v1/auth/login")) {
            maxRequests = MAX_LOGIN_PER_MINUTE;
            keyPrefix = KEY_PREFIX_AUTH + "login:";
            identifier = getClientIp(request);
        } else if (path.equals("/api/v1/auth/register")) {
            maxRequests = MAX_REGISTER_PER_MINUTE;
            keyPrefix = KEY_PREFIX_AUTH + "register:";
            identifier = getClientIp(request);
        } else {
            chain.doFilter(request, response);
            return;
        }

        String key = keyPrefix + identifier;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        Long ttl = redisTemplate.getExpire(key);
        long remaining = Math.max(0, maxRequests - (count != null ? count : 0));
        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset",
            String.valueOf(System.currentTimeMillis() / 1000 + (ttl != null ? ttl : 60)));

        if (count != null && count > maxRequests) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(ttl != null ? ttl : 60));
            ApiError error = ApiError.tooManyRequests(
                "请求过于频繁，请稍后再试", (int) (ttl != null ? ttl : 60));
            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        chain.doFilter(request, response);
    }

    private Long getUserId(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
