package cn.xiaomo.breeze.dependency;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("task_dependencies")
public class TaskDependency {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long dependsOnTaskId;
    private String type;
    private LocalDateTime createdAt;
}
