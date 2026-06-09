package cn.xiaomo.breeze.knowledge.service;

import cn.xiaomo.breeze.attachment.FileStorageService;
import cn.xiaomo.breeze.knowledge.entity.KnowledgeConversation;
import cn.xiaomo.breeze.knowledge.entity.KnowledgeDocument;
import cn.xiaomo.breeze.knowledge.entity.KnowledgeMessage;
import cn.xiaomo.breeze.knowledge.mapper.KnowledgeConversationMapper;
import cn.xiaomo.breeze.knowledge.mapper.KnowledgeMessageMapper;
import cn.xiaomo.breeze.knowledge.parser.DocumentParser;
import cn.xiaomo.breeze.ai.service.PromptTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 知识库 AI 问答服务。
 * <p>
 * 检索文档 → 从 DB 读取 extracted_text（或回退到 DocumentParser）→ 动态截断 → DeepSeek 文本推理 → SSE 流式返回。
 * </p>
 */
@Slf4j
@Service
public class KnowledgeChatService {

    private final ChatClient chatClient;  // DeepSeek（Primary）
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeConversationMapper conversationMapper;
    private final KnowledgeMessageMapper messageMapper;
    private final FileStorageService fileStorageService;
    private final DocumentParser documentParser;
    private final PromptTemplateService promptTemplateService;

    private static final int TOP_K = 5;
    /** RAG Prompt 总 token 预算（约 128K 上下文窗口的 1/4，按 ~4 字符/token 估算） */
    private static final int TOTAL_TOKEN_BUDGET = 32000;
    /** 单篇文档字符数上限（防止超长文档挤占其他文档空间） */
    private static final int MAX_CHARS_PER_DOC = 8000;
    /** 对话历史最大消息条数 */
    private static final int MAX_HISTORY_MESSAGES = 20;
    /** 对话历史 token 预算（估算） */
    private static final int HISTORY_TOKEN_BUDGET = 16000;

    public KnowledgeChatService(ChatClient chatClient,
                                KnowledgeRetrievalService retrievalService,
                                KnowledgeConversationMapper conversationMapper,
                                KnowledgeMessageMapper messageMapper,
                                FileStorageService fileStorageService,
                                DocumentParser documentParser,
                                PromptTemplateService promptTemplateService) {
        this.chatClient = chatClient;
        this.retrievalService = retrievalService;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.fileStorageService = fileStorageService;
        this.documentParser = documentParser;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 流式问答（支持多轮对话，含 Prompt Caching 优化）。
     *
     * <p>使用 Spring AI 标准三角色分离：
     * <pre>
     *   .system(systemPrompt)       ← 静态模板，100% 缓存命中
     *   .messages(historyMessages)  ← 角色保留（User/Assistant），前缀缓存
     *   .user(userPrompt)           ← RAG 文档 + 当前问题，每轮变化
     * </pre>
     */
    public Flux<String> streamChat(Long conversationId, String userMessage, Long userId) {
        // 1. 加载对话历史（正序）
        List<KnowledgeMessage> history = loadHistory(conversationId);

        // 2. 检索文档
        List<KnowledgeRetrievalService.RetrievalResult> results =
                retrievalService.retrieve(userMessage, userId, TOP_K);

        // 3. 构建引用信息（保存到 DB）
        List<Map<String, Object>> references = buildReferences(results);

        // 4. 构建 System Prompt（静态模板，永远缓存）
        String systemPrompt = promptTemplateService.render("knowledge-system-prompt");

        // 5. 构建对话历史消息（Spring AI Message，角色保留）
        List<Message> historyMessages = buildHistoryMessages(history);

        // 6. 构建用户消息（RAG 文档 + 当前问题）
        String userPrompt = buildUserPrompt(results, history, userMessage);

        // 7. 保存用户消息
        KnowledgeMessage userMsg = new KnowledgeMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setTokenCount(estimateTokens(userMessage));
        messageMapper.insert(userMsg);

        // 8. 调用 ChatClient（.system() + .messages() + .user() 三角色分离）
        StringBuilder fullResponse = new StringBuilder();
        return chatClient.prompt()
                .system(systemPrompt)
                .messages(historyMessages)
                .user(userPrompt)
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    KnowledgeMessage assistantMsg = new KnowledgeMessage();
                    assistantMsg.setConversationId(conversationId);
                    assistantMsg.setRole("assistant");
                    assistantMsg.setContent(fullResponse.toString());
                    assistantMsg.setReferencedDocs(references.isEmpty() ? null : references);
                    assistantMsg.setTokenCount(estimateTokens(fullResponse.toString()));
                    messageMapper.insert(assistantMsg);
                    KnowledgeConversation conv = new KnowledgeConversation();
                    conv.setId(conversationId);
                    conv.setUpdatedAt(java.time.LocalDateTime.now());
                    conversationMapper.updateById(conv);
                })
                .doOnError(e -> log.error("知识库问答失败: conversationId={}", conversationId, e));
    }

    /** 构建引用信息列表 */
    private List<Map<String, Object>> buildReferences(
            List<KnowledgeRetrievalService.RetrievalResult> results) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (var result : results) {
            KnowledgeDocument doc = result.document();
            Map<String, Object> ref = new HashMap<>();
            ref.put("id", doc.getId());
            ref.put("title", doc.getTitle());
            ref.put("fileType", doc.getFileType());
            ref.put("score", Math.round(result.score() * 1000) / 10.0);
            references.add(ref);
        }
        return references;
    }

    /** 将对话历史转为 Spring AI Message 列表（保留 user/assistant 角色） */
    private List<Message> buildHistoryMessages(List<KnowledgeMessage> history) {
        List<Message> messages = new ArrayList<>();
        for (var msg : history) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        return messages;
    }

    /** 构建用户消息（XML 标签包裹 RAG 文档 + 当前问题） */
    private String buildUserPrompt(List<KnowledgeRetrievalService.RetrievalResult> results,
                                    List<KnowledgeMessage> history, String userMessage) {
        StringBuilder sb = new StringBuilder();

        // RAG 文档（XML 标签分隔，边界清晰）
        if (results.isEmpty()) {
            sb.append("<documents>\n（未找到相关文档）\n</documents>\n\n");
        } else {
            sb.append("<documents>\n");
            int historyTokens = estimateHistoryTokens(history);
            int docBudget = Math.max(1000,
                    (TOTAL_TOKEN_BUDGET - historyTokens) * 4 / results.size());
            int truncateLen = Math.min(MAX_CHARS_PER_DOC, docBudget);
            for (int i = 0; i < results.size(); i++) {
                var result = results.get(i);
                KnowledgeDocument doc = result.document();
                sb.append("<document index=\"").append(i + 1).append("\"")
                        .append(" title=\"").append(escapeXml(doc.getTitle())).append("\"")
                        .append(" type=\"").append(doc.getFileType()).append("\"")
                        .append(" relevance=\"").append(Math.round(result.score() * 100)).append("%\"")
                        .append(">\n");
                String content = getDocContent(doc);
                if (content != null && !content.isBlank()) {
                    sb.append(content, 0, Math.min(content.length(), truncateLen));
                    sb.append("\n");
                }
                sb.append("</document>\n");
            }
            sb.append("</documents>\n\n");
        }

        // 当前用户问题
        sb.append("用户问题：").append(userMessage);
        return sb.toString();
    }

    /** 转义 XML 特殊字符 */
    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * 加载对话历史（按时间正序），从旧消息开始截断以保持缓存前缀稳定。
     */
    private List<KnowledgeMessage> loadHistory(Long conversationId) {
        if (conversationId == null) return List.of();
        // 按时间正序取最近 N 条
        var qw = new LambdaQueryWrapper<KnowledgeMessage>()
                .eq(KnowledgeMessage::getConversationId, conversationId)
                .orderByAsc(KnowledgeMessage::getCreatedAt)
                .last("LIMIT " + MAX_HISTORY_MESSAGES);
        List<KnowledgeMessage> all = messageMapper.selectList(qw);
        if (all.isEmpty()) return all;

        // 从最旧的消息开始截断（缓存友好：保留的消息在 prompt 中位置和文本都不变）
        int tokenCount = 0;
        int startIdx = all.size();
        for (int i = all.size() - 1; i >= 0; i--) {
            tokenCount += estimateTokens(all.get(i).getContent());
            if (tokenCount > HISTORY_TOKEN_BUDGET) break;
            startIdx = i;
        }
        return all.subList(startIdx, all.size());
    }

    /**
     * 估算对话历史总 token 数。
     */
    private int estimateHistoryTokens(List<KnowledgeMessage> history) {
        int total = 0;
        for (var msg : history) {
            total += estimateTokens(msg.getContent());
        }
        return total;
    }

    /**
     * 获取文档文本内容。
     * 优先从 DB 的 extracted_text 字段读取，为历史数据（NULL）时回退到 DocumentParser 实时解析。
     */
    private String getDocContent(KnowledgeDocument doc) {
        // 优先使用已存储的全文
        if (doc.getExtractedText() != null && !doc.getExtractedText().isBlank()) {
            return doc.getExtractedText();
        }
        // 历史数据回退：从文件存储重新解析
        if (doc.getStorageKey() == null) return null;
        log.debug("文档 {} extracted_text 为空，回退到 DocumentParser 实时解析", doc.getId());
        try (InputStream is = fileStorageService.retrieve(doc.getStorageKey())) {
            return documentParser.parse(is, doc.getFileType(), doc.getFileName());
        } catch (Exception e) {
            log.warn("回退解析文档文本失败: {}", doc.getTitle(), e);
            return null;
        }
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return (int) Math.ceil(text.length() * 0.5);
    }
}
