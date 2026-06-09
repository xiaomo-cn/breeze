package cn.xiaomo.breeze.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文档-标签多对多关联。
 */
@Data
@TableName("knowledge_document_tags")
public class KnowledgeDocumentTag {

    /** 文档 ID */
    private Long documentId;

    /** 标签 ID */
    private Long tagId;
}
