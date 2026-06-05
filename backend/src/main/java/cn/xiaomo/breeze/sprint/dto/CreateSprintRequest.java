package cn.xiaomo.breeze.sprint.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateSprintRequest {
    private String name;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
}
