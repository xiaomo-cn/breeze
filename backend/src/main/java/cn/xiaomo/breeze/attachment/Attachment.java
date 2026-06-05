package cn.xiaomo.breeze.attachment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务附件实体，映射 task_attachments 表。
 */
@Data
@TableName("task_attachments")
public class Attachment {
    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属任务 ID */
    private Long taskId;

    /** 上传者用户 ID */
    private Long userId;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型，如 image/png */
    private String contentType;

    /** 存储键（S3 key 或本地路径） */
    private String storageKey;

    /** 存储提供商: local, minio, oss */
    private String storageProvider;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
