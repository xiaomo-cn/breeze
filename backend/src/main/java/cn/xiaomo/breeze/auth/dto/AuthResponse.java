package cn.xiaomo.breeze.auth.dto;

public record AuthResponse(
    Long userId,
    String username,
    String role,
    boolean mustChangePassword,
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
