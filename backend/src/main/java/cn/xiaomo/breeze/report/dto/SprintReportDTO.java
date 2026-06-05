package cn.xiaomo.breeze.report.dto;

import java.util.List;
import lombok.Data;

@Data
public class SprintReportDTO {
    private Long sprintId;
    private String sprintName;
    private String sprintGoal;
    private String sprintStatus;
    private int totalTasks;
    private int completedTasks;
    private int totalStoryPoints;
    private int completedStoryPoints;
    private double completionRate;
    private List<WeeklyReportDTO.MemberContribution> contributions;
    private List<BurndownPoint> burndown;

    @Data
    public static class BurndownPoint {
        private String date;
        private int idealRemaining;
        private int actualRemaining;
    }
}
