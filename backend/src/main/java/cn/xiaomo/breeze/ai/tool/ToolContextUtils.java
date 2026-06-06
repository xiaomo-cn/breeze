package cn.xiaomo.breeze.ai.tool;

import org.springframework.ai.chat.model.ToolContext;

/**
 * ToolContext 访问通用工具方法。
 * 消除 ReadTools / WriteTools / TaskTools 中的重复代码。
 */
public final class ToolContextUtils {

    private ToolContextUtils() {
    }

    /**
     * 从 ToolContext 中提取会话 ID
     */
    public static Long getConvId(ToolContext ctx) {
        if (ctx == null || ctx.getContext() == null) return null;
        Object val = ctx.getContext().get("conversationId");
        return val instanceof Long l ? l : null;
    }

    /**
     * 从 ToolContext 中提取用户 ID
     */
    public static Long getUserId(ToolContext ctx) {
        if (ctx == null || ctx.getContext() == null) return null;
        Object val = ctx.getContext().get("userId");
        return val instanceof Long l ? l : null;
    }
}
