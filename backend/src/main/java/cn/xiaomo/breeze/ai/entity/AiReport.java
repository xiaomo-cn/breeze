package cn.xiaomo.breeze.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI 生成报告实体，对应 ai_reports 表。
 */
@Data
@TableName("ai_reports")
public class AiReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String type;
    private String title;
    private String content;

    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
}
