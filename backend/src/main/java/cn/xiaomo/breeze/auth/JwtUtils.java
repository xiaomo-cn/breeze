package cn.xiaomo.breeze.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtils {

    private final SecretKey key;
    private final long accessTokenTtl;
    private final long refreshTokenTtl;

    public JwtUtils(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.access-token-ttl}") long accessTokenTtl,
                    @Value("${jwt.refresh-token-ttl}") long refreshTokenTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String generateAccessToken(Long userId, String username, String role) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("role", role)
            .claim("type", "access")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenTtl))
            .signWith(key)
            .compact();
    }

    public String generateRefreshToken(Long userId, String username, String role) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
            .id(jti)
            .subject(userId.toString())
            .claim("username", username)
            .claim("role", role)
            .claim("type", "refresh")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshTokenTtl))
            .signWith(key)
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get("type"));
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("type"));
    }

    public long getAccessTokenTtl() {
        return accessTokenTtl / 1000;
    }

    public long getRefreshTokenTtl() {
        return refreshTokenTtl;
    }
}
