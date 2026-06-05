package cn.xiaomo.breeze.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    String error,
    String message,
    Map<String, String> fieldErrors,
    Integer retryAfterSeconds,
    Instant timestamp
) {
    public static ApiError of(String error, String message) {
        return new ApiError(error, message, null, null, Instant.now());
    }

    public static ApiError badRequest(String message) {
        return new ApiError("bad_request", message, null, null, Instant.now());
    }

    public static ApiError unauthorized(String message) {
        return new ApiError("unauthorized", message, null, null, Instant.now());
    }

    public static ApiError notFound(String message) {
        return new ApiError("not_found", message, null, null, Instant.now());
    }

    public static ApiError conflict(String message) {
        return new ApiError("conflict", message, null, null, Instant.now());
    }

    public static ApiError tooManyRequests(String message, int retryAfterSeconds) {
        return new ApiError("too_many_requests", message, null, retryAfterSeconds, Instant.now());
    }
}
