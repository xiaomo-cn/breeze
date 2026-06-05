package cn.xiaomo.breeze.auth.dto;

public record UpdateProfileRequest(
    String displayName,
    String title,
    Long positionId
) {}
