package cn.xiaomo.breeze.knowledge.controller;

import cn.xiaomo.breeze.knowledge.entity.KnowledgeConversation;
import cn.xiaomo.breeze.knowledge.entity.KnowledgeMessage;
import cn.xiaomo.breeze.knowledge.service.KnowledgeChatService;
import cn.xiaomo.breeze.knowledge.service.KnowledgeConversationService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeChatController {

    private final KnowledgeChatService chatService;
    private final KnowledgeConversationService conversationService;

    /** SSE 流式问答 */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam(required = false) Long conversationId,
            @RequestParam String message,
            Principal principal) {
        Long userId = getUserId(principal);
        if (conversationId == null) {
            KnowledgeConversation conv = conversationService.create(userId);
            conversationId = conv.getId();
        }
        // 首次消息设置标题
        conversationService.updateTitle(conversationId, message);
        return chatService.streamChat(conversationId, message, userId);
    }

    /** 对话列表 */
    @GetMapping("/conversations")
    public ResponseEntity<List<KnowledgeConversation>> listConversations(Principal principal) {
        return ResponseEntity.ok(conversationService.listByUser(getUserId(principal)));
    }

    /** 对话消息 */
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<KnowledgeMessage>> getMessages(@PathVariable Long id) {
        return ResponseEntity.ok(conversationService.getMessages(id));
    }

    /** 删除对话 */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id, Principal principal) {
        conversationService.delete(id, getUserId(principal));
        return ResponseEntity.ok().build();
    }

    private Long getUserId(Principal principal) {
        if (principal == null) throw new RuntimeException("未登录");
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            throw new RuntimeException("无法获取用户信息");
        }
    }
}
