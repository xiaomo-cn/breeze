package cn.xiaomo.breeze.report.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class DailyReportDTO {
    private LocalDate date;
    private List<TaskSummary> completedTasks;
    private List<TaskSummary> inProgressTasks;
    private List<TaskSummary> blockedTasks;
    private int createdCount;
    private int completedCount;
}
