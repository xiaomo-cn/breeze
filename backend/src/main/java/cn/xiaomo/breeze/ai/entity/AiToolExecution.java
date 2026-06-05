package cn.xiaomo.breeze.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.xiaomo.breeze.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
@TableName(value = "ai_tool_executions", autoResultMap = true)
public class AiToolExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private Long messageId;
    private Long userId;
    private String toolName;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> toolInput;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> toolOutput;

    private String status;
    private Integer durationMs;

    private LocalDateTime createdAt;
}
