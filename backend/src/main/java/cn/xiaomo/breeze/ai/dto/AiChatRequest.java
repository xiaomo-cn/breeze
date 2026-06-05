package cn.xiaomo.breeze.ai.dto;

public record AiChatRequest(
    Long projectId,
    String message,
    Long conversationId
) {}
