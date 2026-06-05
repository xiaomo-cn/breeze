package cn.xiaomo.breeze.comment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务评论实体，映射 task_comments 表。支持一级回复（parentId 非空表示回复）。
 */
@Data
@TableName("task_comments")
public class Comment {
    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务 ID */
    private Long taskId;

    /** 父评论 ID（null 表示顶级评论，非 null 表示回复） */
    private Long parentId;

    /** 评论者用户 ID */
    private Long userId;

    /** 评论内容（Markdown） */
    private String content;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
