package cn.xiaomo.breeze.task;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务协作人关联实体，映射 task_collaborators 表。
 */
@Data
@TableName("task_collaborators")
public class TaskCollaborator {

    /** 任务 ID */
    private Long taskId;

    /** 协作人用户 ID */
    private Long userId;

    /** 添加时间 */
    private LocalDateTime createdAt;
}
