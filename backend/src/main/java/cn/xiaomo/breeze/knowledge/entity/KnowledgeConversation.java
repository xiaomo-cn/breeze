package cn.xiaomo.breeze.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库 AI 问答对话实体。
 */
@Data
@TableName("knowledge_conversations")
public class KnowledgeConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 对话标题 */
    private String title;

    /** 使用的模型（复用 DeepSeek，与项目 AI 一致） */
    private String model = "deepseek-v4-pro";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
