package cn.xiaomo.breeze.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库文档与文件夹实体。
 * <p>
 * 文件夹：fileType = 'folder'，fileSize = 0，storageKey = NULL
 * 文档：fileType = pdf/docx/... 等
 * </p>
 */
@Data
@TableName("knowledge_documents")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父文件夹 ID，NULL 表示根目录 */
    private Long parentFolderId;

    /** 文档/文件夹标题 */
    private String title;

    /** 描述 */
    private String description;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型：folder / pdf / docx / xlsx / pptx / md / txt / png / jpg / html / csv */
    private String fileType;

    /** 文件大小（字节），文件夹为 0 */
    private Long fileSize;

    /** SHA-256 文件哈希 */
    private String fileHash;

    /** 文件存储路径 */
    private String storageKey;

    /** 向量切片数量 */
    private Integer chunkCount;

    /** 子项数量（仅文件夹有效，查询时通过子查询计算，非数据库字段） */
    @TableField(exist = false)
    private Integer childCount;

    /** 向量化状态：pending / processing / completed / failed */
    private String embeddingStatus;

    /** Tika/DocumentParser 提取的纯文本全文（TEXT 列）。上传时填充，问答时直接读取避免重复解析 */
    private String extractedText;

    /** 创建者 ID */
    private Long createdBy;

    /** 更新者 ID */
    private Long updatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 软删除标记 */
    @TableField("is_deleted")
    private Boolean isDeleted;
}
