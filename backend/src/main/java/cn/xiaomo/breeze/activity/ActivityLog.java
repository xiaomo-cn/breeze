package cn.xiaomo.breeze.activity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.xiaomo.breeze.common.JsonbTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/**
 * 活动日志实体，映射 activity_log 表。
 * 记录项目内的操作审计日志（创建/更新/删除任务等）。
 */
@Data
@TableName(value = "activity_log", autoResultMap = true)
public class ActivityLog {
    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    private Long projectId;

    /** 操作用户 ID */
    private Long userId;

    /** 操作类型：created / updated / deleted / status_changed */
    private String actionType;

    /** 实体类型：task / comment / sprint */
    private String entityType;

    /** 实体 ID */
    private Long entityId;

    /** 操作详情（JSONB），如 {title: "...", status: "done"} */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> details;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
