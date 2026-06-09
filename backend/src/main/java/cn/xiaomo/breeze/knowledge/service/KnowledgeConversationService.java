package cn.xiaomo.breeze.knowledge.service;

import cn.xiaomo.breeze.knowledge.entity.KnowledgeConversation;
import cn.xiaomo.breeze.knowledge.entity.KnowledgeMessage;
import cn.xiaomo.breeze.knowledge.mapper.KnowledgeConversationMapper;
import cn.xiaomo.breeze.knowledge.mapper.KnowledgeMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 知识库对话管理服务 */
@Service
@RequiredArgsConstructor
public class KnowledgeConversationService {

    private final KnowledgeConversationMapper conversationMapper;
    private final KnowledgeMessageMapper messageMapper;

    /** 创建新对话 */
    public KnowledgeConversation create(Long userId) {
        KnowledgeConversation conv = new KnowledgeConversation();
        conv.setUserId(userId);
        conv.setTitle("新对话");
        conv.setModel("deepseek-v4-pro");
        conversationMapper.insert(conv);
        return conv;
    }

    /** 用户的历史对话列表 */
    public List<KnowledgeConversation> listByUser(Long userId) {
        LambdaQueryWrapper<KnowledgeConversation> qw = new LambdaQueryWrapper<>();
        qw.eq(KnowledgeConversation::getUserId, userId)
          .orderByDesc(KnowledgeConversation::getUpdatedAt);
        return conversationMapper.selectList(qw);
    }

    /** 对话消息列表 */
    public List<KnowledgeMessage> getMessages(Long conversationId) {
        LambdaQueryWrapper<KnowledgeMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(KnowledgeMessage::getConversationId, conversationId)
          .orderByAsc(KnowledgeMessage::getCreatedAt);
        return messageMapper.selectList(qw);
    }

    /** 自动标题 */
    public void updateTitle(Long conversationId, String firstMessage) {
        KnowledgeConversation conv = conversationMapper.selectById(conversationId);
        if (conv != null && "新对话".equals(conv.getTitle()) && firstMessage != null) {
            String title = firstMessage.length() > 30
                    ? firstMessage.substring(0, 30)
                    : firstMessage;
            conv.setTitle(title);
            conversationMapper.updateById(conv);
        }
    }

    /** 删除对话 */
    public void delete(Long conversationId, Long userId) {
        KnowledgeConversation conv = conversationMapper.selectById(conversationId);
        if (conv != null && conv.getUserId().equals(userId)) {
            conversationMapper.deleteById(conversationId);
            // 删除关联消息
            messageMapper.delete(new LambdaQueryWrapper<KnowledgeMessage>()
                    .eq(KnowledgeMessage::getConversationId, conversationId));
        }
    }
}
