package cn.xiaomo.breeze.gantt.dto;

import java.util.List;
import lombok.Data;

@Data
public class GanttData {
    private List<GanttTask> tasks;

    @Data
    public static class GanttTask {
        private Long id;
        private String key;
        private String title;
        private String startDate;
        private String endDate;
        private String assigneeName;
        private String status;
        private List<Long> dependencies;
    }
}
