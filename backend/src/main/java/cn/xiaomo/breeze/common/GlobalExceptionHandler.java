package cn.xiaomo.breeze.common;

import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
            fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
            new ApiError("validation_error", "Invalid request", fieldErrors, null, java.time.Instant.now()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiError.unauthorized("Invalid username or password"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of("forbidden", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiError.badRequest(ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRuntime(RuntimeException ex, HttpServletResponse response) {
        // SSE 流式响应已提交 text/event-stream，无法再返回 JSON
        if (isSseResponse(response)) {
            if (isClientDisconnect(ex)) {
                log.debug("SSE client disconnected: {}", ex.getMessage());
            } else {
                log.error("Unhandled runtime exception in SSE endpoint", ex);
            }
            return null;
        }
        log.error("Internal server error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of("internal_error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex, HttpServletResponse response) {
        // SSE 流式响应已提交 text/event-stream，无法再返回 JSON
        if (isSseResponse(response)) {
            if (isClientDisconnect(ex)) {
                log.debug("SSE client disconnected: {}", ex.getMessage());
            } else {
                log.error("Unhandled exception in SSE endpoint", ex);
            }
            return null;
        }
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of("internal_error", "An unexpected error occurred"));
    }

    private boolean isSseResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        return contentType != null && contentType.contains("event-stream");
    }

    /**
     * 判断是否为客户端主动断开连接导致的异常。
     * 客户端关闭页面、刷新、网络切换等是正常行为，不应记录为 ERROR。
     */
    private boolean isClientDisconnect(Throwable e) {
        if (e == null) return false;
        // 先检查直接异常
        if (isClientDisconnectMessage(e)) return true;
        // 再检查被包装的 cause（如 RuntimeException 包装了 IOException）
        return isClientDisconnect(e.getCause());
    }

    private boolean isClientDisconnectMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        // Windows: "你的主机中的软件中止了一个已建立的连接"
        // Linux/Mac: "Broken pipe" / "Connection reset by peer"
        return msg.contains("中止了一个已建立的连接")
            || msg.contains("abort")
            || msg.contains("Broken pipe")
            || msg.contains("Connection reset")
            || msg.contains("connection was aborted");
    }
}
