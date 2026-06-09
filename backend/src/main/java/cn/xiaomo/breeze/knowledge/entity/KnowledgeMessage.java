package cn.xiaomo.breeze.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.xiaomo.breeze.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 知识库 AI 问答消息实体。
 */
@Data
@TableName(value = "knowledge_messages", autoResultMap = true)
public class KnowledgeMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属对话 ID */
    private Long conversationId;

    /** 角色：user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /** 引用的文档列表 [{id, title, fileType, pageNumber}] */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<Map<String, Object>> referencedDocs;

    /** Token 数量 */
    private Integer tokenCount;

    private LocalDateTime createdAt;
}
