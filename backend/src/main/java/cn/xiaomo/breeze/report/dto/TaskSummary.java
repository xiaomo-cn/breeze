package cn.xiaomo.breeze.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskSummary {
    private Long id;
    private String key;
    private String title;
    private String status;
    private String priority;
    private String assigneeName;
}
