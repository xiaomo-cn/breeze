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
@TableName(value = "ai_messages", autoResultMap = true)
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;
    private String role;
    private String content;
    private Integer tokenCount;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> metadata;

    private LocalDateTime createdAt;
}
