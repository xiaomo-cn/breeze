package cn.xiaomo.breeze.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 通知实体，映射 notifications 表。
 * 支持的类型：TASK_ASSIGNED（任务分配）、COMMENT_ADDED（评论新增）、MENTIONED（@提及）。
 */
@Data
@TableName("notifications")
public class Notification {
    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收通知的用户 ID */
    private Long userId;

    /** 通知类型：TASK_ASSIGNED / COMMENT_ADDED / MENTIONED */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知正文（截断后的内容摘要） */
    private String body;

    /** 关联实体类型：task / comment */
    private String referenceType;

    /** 关联实体 ID */
    private Long referenceId;

    /** 是否已读 */
    private Boolean isRead;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
