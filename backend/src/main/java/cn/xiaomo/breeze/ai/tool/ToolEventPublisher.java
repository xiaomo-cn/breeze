package cn.xiaomo.breeze.ai.tool;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 工具事件发布器 — 使用 Reactor Sinks.Many 实现响应式事件总线。
 * <p>
 * 每个 AI 对话创建独立的 {@link Sinks.Many} 实例，
 * @Tool 方法在执行前后发布事件，SSE 流合并推送至前端。
 */
@Slf4j
@Component
public class ToolEventPublisher {

    private final Map<Long, Sinks.Many<ToolEvent>> sessionSinks = new ConcurrentHashMap<>();

    /**
     * 为指定对话创建事件通道。
     *
     * @param conversationId 对话 ID（用 resolvedConvId 标识），为 null 时返回空 Flux
     * @return 该对话的工具事件 Flux（ServerSentEvent，由 Spring 自动序列化为 SSE）
     */
    public Flux<ServerSentEvent<String>> createSession(Long conversationId) {
        if (conversationId == null) {
            log.warn("createSession called with null conversationId, returning empty Flux");
            return Flux.empty();
        }
        Sinks.Many<ToolEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(conversationId, sink);
        return sink.asFlux()
            .map(ToolEvent::toServerSentEvent)
            .doOnCancel(() -> removeSession(conversationId))
            .doOnTerminate(() -> removeSession(conversationId));
    }

    /**
     * 发布工具开始事件。conversationId 为 null 时静默跳过（跨线程时 ThreadLocal 未传播）。
     */
    public void publishStart(Long conversationId, String toolName, String description) {
        if (conversationId == null) {
            log.debug("publishStart skipped: conversationId is null (likely cross-thread tool call)");
            return;
        }
        Sinks.Many<ToolEvent> sink = sessionSinks.get(conversationId);
        if (sink != null) {
            sink.tryEmitNext(ToolEvent.start(toolName, description));
        }
    }

    /**
     * 发布工具完成事件。conversationId 为 null 时静默跳过。
     */
    public void publishEnd(Long conversationId, String toolName, String result, boolean success) {
        if (conversationId == null) {
            log.debug("publishEnd skipped: conversationId is null (likely cross-thread tool call)");
            return;
        }
        Sinks.Many<ToolEvent> sink = sessionSinks.get(conversationId);
        if (sink != null) {
            sink.tryEmitNext(ToolEvent.end(toolName, result, success));
        }
    }

    /**
     * 发布工具确认事件（需要用户确认才能执行）。conversationId 为 null 时静默跳过。
     */
    public void publishConfirmation(Long conversationId, String toolName, String description,
                                     String pendingId, String confirmDetails) {
        if (conversationId == null) {
            log.debug("publishConfirmation skipped: conversationId is null (likely cross-thread tool call)");
            return;
        }
        Sinks.Many<ToolEvent> sink = sessionSinks.get(conversationId);
        if (sink != null) {
            sink.tryEmitNext(ToolEvent.confirmation(toolName, description, pendingId, confirmDetails));
        }
    }

    /**
     * 关闭并移除对话的事件通道。
     */
    public void completeSession(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        Sinks.Many<ToolEvent> sink = sessionSinks.remove(conversationId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    private void removeSession(Long conversationId) {
        Sinks.Many<ToolEvent> sink = sessionSinks.remove(conversationId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    // ---- 内部事件模型 ----

    record ToolEvent(String type, String toolName, String message, long timestamp,
                     String pendingId, String confirmDetails) {
        static ToolEvent start(String toolName, String description) {
            return new ToolEvent("tool_start", toolName,
                description != null ? description : toolName,
                Instant.now().toEpochMilli(), null, null);
        }

        static ToolEvent end(String toolName, String result, boolean success) {
            return new ToolEvent("tool_end", toolName,
                (success ? "" : "❌ ") + (result != null ? result : ""),
                Instant.now().toEpochMilli(), null, null);
        }

        static ToolEvent confirmation(String toolName, String description,
                                       String pendingId, String confirmDetails) {
            return new ToolEvent("tool_confirmation", toolName, description,
                Instant.now().toEpochMilli(), pendingId, confirmDetails);
        }

        /**
         * 转为 Spring ServerSentEvent，由框架自动序列化为 SSE 格式。
         */
        ServerSentEvent<String> toServerSentEvent() {
            StringBuilder data = new StringBuilder();
            data.append("{\"toolName\":\"").append(escape(toolName)).append("\"")
                .append(",\"message\":\"").append(escape(message)).append("\"")
                .append(",\"timestamp\":").append(timestamp);
            if (pendingId != null) {
                data.append(",\"pendingId\":\"").append(escape(pendingId)).append("\"");
            }
            if (confirmDetails != null) {
                data.append(",\"confirmDetails\":\"").append(escape(confirmDetails)).append("\"");
            }
            data.append("}");
            return ServerSentEvent.<String>builder()
                .event(type)
                .data(data.toString())
                .build();
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        }
    }
}
