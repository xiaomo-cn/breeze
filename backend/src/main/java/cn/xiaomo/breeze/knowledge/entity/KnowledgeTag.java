package cn.xiaomo.breeze.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库标签实体（全局共享）。
 */
@Data
@TableName("knowledge_tags")
public class KnowledgeTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名称（全局唯一） */
    private String name;

    /** 颜色 hex 值 */
    private String color;

    private LocalDateTime createdAt;
}
