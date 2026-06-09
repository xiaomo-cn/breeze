package cn.xiaomo.breeze.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文档权限实体。
 */
@Data
@TableName("knowledge_document_permissions")
public class KnowledgeDocumentPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档 ID */
    private Long documentId;

    /** 用户 ID */
    private Long userId;

    /** 权限：read / manage */
    private String permission;

    /** 授权人 ID */
    private Long grantedBy;

    private LocalDateTime createdAt;
}
