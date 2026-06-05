package cn.xiaomo.breeze.sprint.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BurndownPoint {
    private String date;
    private int idealRemaining;
    private int actualRemaining;
}
