package cn.xiaomo.breeze.dependency.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DependencyDTO {
    private Long id;
    private Long taskId;
    private Long dependsOnTaskId;
    private String dependsOnTaskKey;
    private String dependsOnTaskTitle;
    private String type;
    private LocalDateTime createdAt;
}
