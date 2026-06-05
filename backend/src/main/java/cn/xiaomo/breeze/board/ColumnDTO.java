package cn.xiaomo.breeze.board;

import lombok.Data;

@Data
public class ColumnDTO {

    private Long id;
    private String name;
    private String statusMapping;
    private Integer wipLimit;
    private Integer sortOrder;
    private String color;
}
