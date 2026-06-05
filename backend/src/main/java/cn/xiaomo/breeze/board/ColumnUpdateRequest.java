package cn.xiaomo.breeze.board;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ColumnUpdateRequest {

    @Size(max = 100)
    private String name;

    @Size(max = 50)
    private String statusMapping;

    private Integer wipLimit;

    private Integer sortOrder;

    @Size(max = 20)
    private String color;
}
