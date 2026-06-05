package cn.xiaomo.breeze.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class WeeklyReportDTO {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<DailyPoint> dailyPoints;
    private Map<String, Integer> taskDistribution;
    private List<MemberContribution> contributions;
    private int newTasks;
    private int completedTasks;
    private int remainingTasks;

    @Data
    public static class DailyPoint {
        private LocalDate date;
        private int created;
        private int completed;
    }

    @Data
    public static class MemberContribution {
        private String userName;
        private int completed;
        private int created;
    }
}
