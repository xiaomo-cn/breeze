package cn.xiaomo.breeze.ai.tool;

/**
 * 请求级 AI 上下文，通过 ThreadLocal 传递 userId 和 conversationId。
 * 由 {@link cn.xiaomo.breeze.ai.service.AiAgentService#streamChat} 设置。
 *
 * <p>⚠️ 注意：ThreadLocal 在反应式线程中不可用。工具方法应使用
 * {@code ToolContext} 参数获取上下文，而非此类。
 */
public final class AiRequestContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> CONVERSATION_ID = new ThreadLocal<>();

    private AiRequestContext() {}

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setConversationId(Long conversationId) {
        CONVERSATION_ID.set(conversationId);
    }

    public static Long getConversationId() {
        return CONVERSATION_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
        CONVERSATION_ID.remove();
    }
}
