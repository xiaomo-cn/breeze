package cn.xiaomo.breeze.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ColumnCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 50)
    private String statusMapping;

    private Integer wipLimit = 0;

    private Integer sortOrder = 0;

    @Size(max = 20)
    private String color = "#808080";
}
