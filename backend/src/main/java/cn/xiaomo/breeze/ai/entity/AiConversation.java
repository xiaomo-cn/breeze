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
@TableName(value = "ai_conversations", autoResultMap = true)
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long projectId;
    private String title;
    private String model;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> contextSnapshot;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
