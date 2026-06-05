package cn.xiaomo.breeze.board;

import java.util.List;
import lombok.Data;

@Data
public class BoardDTO {

    private Long id;
    private Long projectId;
    private String name;
    private Boolean isDefault;
    private List<ColumnDTO> columns;
}
