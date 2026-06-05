package cn.xiaomo.breeze.event;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE completed for user {}", userId);
            removeEmitter(userId, emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for user {}", userId);
            removeEmitter(userId, emitter);
        });
        emitter.onError(e -> {
            // 客户端主动断开连接是正常行为（关闭标签页、刷新页面等），不需要打印堆栈
            if (isClientDisconnect(e)) {
                log.debug("SSE client disconnected for user {}", userId);
            } else {
                log.warn("SSE error for user {}", userId, e);
            }
            removeEmitter(userId, emitter);
        });

        // Send initial heartbeat
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
            return emitter;
        }

        // Periodic heartbeat to keep connection alive (every 60s)
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                Set<SseEmitter> userEmitters = emitters.get(userId);
                if (userEmitters != null && userEmitters.contains(emitter)) {
                    emitter.send(SseEmitter.event().name("heartbeat").data("{}"));
                }
            } catch (IOException e) {
                // 客户端断开是正常行为，无需打印堆栈
                if (isClientDisconnect(e)) {
                    log.debug("Heartbeat: client disconnected for user {}", userId);
                } else {
                    log.warn("Heartbeat failed for user {}", userId, e);
                }
                removeEmitter(userId, emitter);
            }
        }, 60, 60, TimeUnit.SECONDS);

        return emitter;
    }

    public void broadcast(List<Long> userIds, String eventName, String data) {
        for (Long userId : userIds) {
            send(userId, eventName, data);
        }
    }

    public void send(Long userId, String eventName, String data) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                } catch (IOException e) {
                    if (isClientDisconnect(e)) {
                        log.debug("SSE send skipped (client disconnected) for user {}", userId);
                    } else {
                        log.warn("Failed to send SSE to user {}", userId, e);
                    }
                    removeEmitter(userId, emitter);
                }
            }
        }
    }

    /**
     * 判断是否为客户端主动断开连接导致的 IOException。
     * 这类异常是正常行为（关闭页面、刷新、网络切换等），不应打印堆栈。
     */
    private boolean isClientDisconnect(Throwable e) {
        if (e == null) return false;
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

    private void removeEmitter(Long userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
