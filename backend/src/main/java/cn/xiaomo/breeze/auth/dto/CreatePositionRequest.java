package cn.xiaomo.breeze.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePositionRequest(
    @NotBlank @Size(min = 1, max = 50) String name,
    @Size(max = 20) String color
) {}
