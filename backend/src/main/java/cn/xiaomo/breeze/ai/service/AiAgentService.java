package cn.xiaomo.breeze.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.ai.entity.AiConversation;
import cn.xiaomo.breeze.ai.entity.AiMessage;
import cn.xiaomo.breeze.ai.entity.AiToolExecution;
import cn.xiaomo.breeze.ai.mapper.AiConversationMapper;
import cn.xiaomo.breeze.ai.mapper.AiMessageMapper;
import cn.xiaomo.breeze.ai.mapper.AiToolExecutionMapper;
import cn.xiaomo.breeze.ai.tool.AiRequestContext;
import cn.xiaomo.breeze.ai.tool.ToolEventPublisher;
import cn.xiaomo.breeze.ai.util.TokenCounter;
import cn.xiaomo.breeze.auth.User;
import cn.xiaomo.breeze.auth.UserMapper;
import cn.xiaomo.breeze.project.ProjectService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentService {

    private final ChatClient chatClient;
    private final RagService ragService;
    private final PromptTemplateService promptTemplateService;
    private final ProjectService projectService;
    private final UserMapper userMapper;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiToolExecutionMapper toolExecutionMapper;
    private final TokenCounter tokenCounter;
    private final ToolEventPublisher eventPublisher;

    /** 上下文窗口 token 上限 */
    private static final int MAX_CONTEXT_TOKENS = 16000;
    /** 为 AI 回复预留的 token */
    private static final int RESERVED_RESPONSE_TOKENS = 4096;

    public Flux<ServerSentEvent<String>> streamChat(Long projectId, Long userId, String userMessage,
                                    Long conversationId) {
        // 1. 加载对话 + 摘要信息（必须在 buildSystemPrompt 之前，因为需要注入摘要）
        AiConversation conv = conversationId != null
            ? conversationMapper.selectById(conversationId) : null;
        String conversationSummary = null;
        List<Message> loadedHistory;

        if (conv != null) {
            Map<String, Object> snapshot = conv.getContextSnapshot();
            if (snapshot != null && !snapshot.isEmpty()) {
                conversationSummary = (String) snapshot.get("summary");
            }
            Long afterId = (snapshot != null) ? (Long) snapshot.get("summarizedUpToId") : null;
            loadedHistory = loadConversationHistory(conversationId, 40, afterId);
        } else {
            loadedHistory = List.of();
        }

        // 2. 构建系统 prompt（包含对话摘要）
        String ragContext = ragService.buildRagContext(projectId, userMessage, userId);
        String systemPrompt = buildSystemPrompt(projectId, userId, ragContext, conversationSummary);

        // 3. 估算 token 消耗 + token 预算裁剪
        int systemTokens = tokenCounter.estimate(systemPrompt);
        int userMsgTokens = tokenCounter.estimate(userMessage);
        int budgetRemaining = MAX_CONTEXT_TOKENS - systemTokens - userMsgTokens
            - RESERVED_RESPONSE_TOKENS;

        // 标记为 effectively final
        final List<Message> history;
        if (!loadedHistory.isEmpty() && conv != null) {
            int historyTokens = loadedHistory.stream()
                .mapToInt(m -> tokenCounter.estimate(m.getText()))
                .sum();
            if (historyTokens > budgetRemaining && loadedHistory.size() > 20) {
                history = List.copyOf(loadedHistory.subList(
                    Math.max(0, loadedHistory.size() - 20), loadedHistory.size()));
            } else {
                history = loadedHistory;
            }
        } else {
            history = loadedHistory;
        }

        final Long resolvedConvId;
        if (conversationId == null) {
            resolvedConvId = createConversation(userId, projectId, userMessage);
        } else {
            resolvedConvId = conversationId;
        }

        AiMessage userMsg = new AiMessage();
        userMsg.setConversationId(resolvedConvId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setTokenCount(userMsgTokens);
        userMsg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(userMsg);

        // 设置请求级上下文（ThreadLocal，非反应式场景使用）
        AiRequestContext.setUserId(userId);
        AiRequestContext.setConversationId(resolvedConvId);

        // 创建工具事件流，合并到 SSE 推送给前端
        Flux<ServerSentEvent<String>> toolEvents = eventPublisher.createSession(resolvedConvId)
            .doOnSubscribe(s -> log.debug("Tool event stream started for conversation {}", resolvedConvId));

        return Flux.defer(() -> {
            var response = new AtomicReference<>("");
            Flux<ServerSentEvent<String>> chatContent = chatClient.prompt()
                .system(systemPrompt)
                .messages(history)
                .user(userMessage)
                .toolContext(Map.of(
                    "conversationId", resolvedConvId,
                    "userId", userId
                ))
                .stream()
                .content()
                .doOnNext(chunk -> response.updateAndGet(s -> s + chunk))
                .doOnComplete(() -> {
                    AiMessage assistantMsg = saveAssistantMessage(resolvedConvId, response.get());
                    linkRecentToolExecutions(resolvedConvId, assistantMsg.getId(), userId);
                    AiConversation resolvedConv = conversationMapper.selectById(resolvedConvId);
                    if (resolvedConv != null) {
                        resolvedConv.setUpdatedAt(LocalDateTime.now());
                        conversationMapper.updateById(resolvedConv);
                    }
                    // 异步生成对话摘要（消息超过阈值时）
                    summarizeConversationAsync(resolvedConvId, assistantMsg.getId());
                    // AI 回复完成，关闭工具事件通道以终止合并流
                    eventPublisher.completeSession(resolvedConvId);
                })
                .doOnError(error -> {
                    saveAssistantMessage(resolvedConvId,
                        "[Error] " + error.getMessage());
                    eventPublisher.completeSession(resolvedConvId);
                })
                .doOnCancel(() -> {
                    // 客户端断开时保存部分响应，避免用户消息成为孤儿
                    String partial = response.get();
                    if (partial != null && !partial.isEmpty()) {
                        saveAssistantMessage(resolvedConvId,
                            partial + "\n\n[对话已中断]");
                    } else {
                        saveAssistantMessage(resolvedConvId, "[对话已中断]");
                    }
                    eventPublisher.completeSession(resolvedConvId);
                })
                // Spring 自动序列化 ServerSentEvent，无需手动拼接 data: 前缀
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());

            return Flux.merge(chatContent, toolEvents)
                .doFinally(signalType -> {
                    // 确保工具事件通道已关闭（doOnComplete/Error/Cancel 中已调用，此处作为兜底）
                    eventPublisher.completeSession(resolvedConvId);
                });
        });
    }

    private String buildSystemPrompt(Long projectId, Long userId, String ragContext,
                                      String conversationSummary) {
        var project = projectService.getById(projectId);
        User user = userMapper.selectById(userId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("projectName", project.getName());
        vars.put("projectId", projectId.toString());
        vars.put("userName", user.getDisplayName());
        vars.put("currentDate", LocalDate.now().toString());
        vars.put("ragContext", ragContext);
        vars.put("conversationSummary", conversationSummary);
        vars.put("toolList", """
                - create_task: 创建新任务
                - search_tasks: 搜索任务
                - list_members: 查看项目成员
                - update_task: 更新任务
                - assign_task: 分配任务
                - add_comment: 添加评论
                - create_subtasks: 创建子任务
                - add_to_sprint: 添加到 Sprint
                - get_task_detail: 查看任务详情
                - get_sprint_status: 查看 Sprint 进度
                - get_user_workload: 查看成员负载
                """);

        return promptTemplateService.render("system-prompt", vars);
    }

    private Long createConversation(Long userId, Long projectId, String firstMessage) {
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setProjectId(projectId);
        conv.setTitle(firstMessage.length() > 50
            ? firstMessage.substring(0, 50) + "..."
            : firstMessage);
        conv.setModel("deepseek-v4-pro");
        conv.setContextSnapshot(Map.of());
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv.getId();
    }

    /**
     * 加载最近 N 条对话历史（按时间倒序取，再反转为升序）。
     *
     * @param afterId 如果非 null，只加载 id 大于此值的消息（即摘要点之后的消息）
     */
    private List<Message> loadConversationHistory(Long conversationId, int limit, Long afterId) {
        var wrapper = new LambdaQueryWrapper<AiMessage>()
            .eq(AiMessage::getConversationId, conversationId);
        if (afterId != null) {
            wrapper.gt(AiMessage::getId, afterId);
        }
        wrapper.orderByDesc(AiMessage::getCreatedAt)
            .last("LIMIT " + limit);
        List<AiMessage> messages = messageMapper.selectList(wrapper);

        // 反转为升序（最早的消息在前）
        List<Message> history = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiMessage msg = messages.get(i);
            if ("user".equals(msg.getRole())) {
                history.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                history.add(new AssistantMessage(msg.getContent()));
            }
        }
        return history;
    }

    /**
     * 保存助手消息并返回含 ID 的实体。
     */
    private AiMessage saveAssistantMessage(Long conversationId, String content) {
        AiMessage msg = new AiMessage();
        msg.setConversationId(conversationId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setTokenCount(tokenCounter.estimate(content));
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        return msg;
    }

    /**
     * 关联最近 10 秒内的未关联工具执行记录到当前对话。
     * 按 userId 精确过滤，防止并发场景下的跨对话串扰。
     */
    private void linkRecentToolExecutions(Long conversationId, Long messageId, Long userId) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(10);
            List<AiToolExecution> recentExecs = toolExecutionMapper.selectList(
                new LambdaQueryWrapper<AiToolExecution>()
                    .isNull(AiToolExecution::getConversationId)
                    .eq(AiToolExecution::getUserId, userId)
                    .ge(AiToolExecution::getCreatedAt, cutoff));
            for (AiToolExecution exec : recentExecs) {
                exec.setConversationId(conversationId);
                exec.setMessageId(messageId);
                toolExecutionMapper.updateById(exec);
            }
        } catch (Exception e) {
            log.error("Failed to link recent tool executions: conversationId={}, messageId={}, userId={}", conversationId, messageId, userId, e);
        }
    }

    /** 触发摘要生成的消息数阈值（用户+助手各算一条，即 10 轮对话） */
    private static final int SUMMARY_THRESHOLD = 20;
    /** 保留最近的不被摘要的消息数 */
    private static final int KEEP_RECENT = 10;

    /**
     * 异步生成对话摘要。当对话消息数超过 {@link #SUMMARY_THRESHOLD} 时，
     * 对早期消息进行 AI 摘要并存储到 {@code contextSnapshot}。
     * <p>
     * 此方法由 {@code doOnComplete} 触发，在独立线程中执行，不阻塞 SSE 响应。
     */
    @Async
    public void summarizeConversationAsync(Long conversationId, Long latestMessageId) {
        try {
            // 统计消息总数
            Long totalMessages = messageMapper.selectCount(
                new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getConversationId, conversationId));
            if (totalMessages <= SUMMARY_THRESHOLD) {
                return;
            }

            // 检查是否已有摘要且摘要点未过期
            AiConversation conv = conversationMapper.selectById(conversationId);
            if (conv == null) return;
            Map<String, Object> snapshot = conv.getContextSnapshot();
            if (snapshot != null && !snapshot.isEmpty()) {
                Long summarizedUpToId = (Long) snapshot.get("summarizedUpToId");
                // 如果上次摘要后新增消息不足阈值，跳过
                if (summarizedUpToId != null) {
                    Long newSinceSummary = messageMapper.selectCount(
                        new LambdaQueryWrapper<AiMessage>()
                            .eq(AiMessage::getConversationId, conversationId)
                            .gt(AiMessage::getId, summarizedUpToId));
                    if (newSinceSummary <= SUMMARY_THRESHOLD) {
                        return;
                    }
                }
            }

            // 获取要被摘要的早期消息
            List<AiMessage> oldMessages = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getConversationId, conversationId)
                    .orderByAsc(AiMessage::getCreatedAt)
                    .last("LIMIT " + (totalMessages.intValue() - KEEP_RECENT)));

            if (oldMessages.isEmpty()) return;

            String summary = generateSummary(oldMessages);
            if (summary == null || summary.isBlank()) return;

            // 存储摘要到 contextSnapshot
            AiMessage lastOld = oldMessages.get(oldMessages.size() - 1);
            Map<String, Object> newSnapshot = new HashMap<>();
            newSnapshot.put("summary", summary);
            newSnapshot.put("summarizedUpToId", lastOld.getId());
            newSnapshot.put("summarizedAt", LocalDateTime.now().toString());
            conv.setContextSnapshot(newSnapshot);
            conversationMapper.updateById(conv);

            log.info("Conversation {} summarized: {} messages → {} chars summary",
                conversationId, oldMessages.size(), summary.length());
        } catch (Exception e) {
            log.error("Failed to summarize conversation {}", conversationId, e);
        }
    }

    /**
     * 调用 AI 生成消息摘要。
     */
    private String generateSummary(List<AiMessage> messages) {
        StringBuilder msgs = new StringBuilder();
        for (AiMessage m : messages) {
            msgs.append("[").append(m.getRole()).append("]: ")
                .append(m.getContent().length() > 200
                    ? m.getContent().substring(0, 200) + "..."
                    : m.getContent())
                .append("\n");
        }

        String prompt = promptTemplateService.render("summary-prompt", Map.of(
                "conversationText", msgs.toString()
        ));

        try {
            return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        } catch (Exception e) {
            log.error("AI summary generation failed", e);
            return null;
        }
    }
}
