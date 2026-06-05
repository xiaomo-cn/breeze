package cn.xiaomo.breeze.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.xiaomo.breeze.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 待确认的工具操作 — 写操作需要用户确认后执行。
 */
@Data
@TableName(value = "pending_tool_actions", autoResultMap = true)
public class PendingToolAction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String toolName;
    private String description;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> paramsJson;

    private String status;  // pending / confirmed / rejected / expired
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
