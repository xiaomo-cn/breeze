package cn.xiaomo.breeze.sprint.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SprintDTO {
    private Long id;
    private Long projectId;
    private String name;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer sortOrder;
    private int taskCount;
    private int completedTaskCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
