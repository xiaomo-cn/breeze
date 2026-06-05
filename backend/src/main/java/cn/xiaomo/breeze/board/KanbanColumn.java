package cn.xiaomo.breeze.board;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("kanban_columns")
public class KanbanColumn {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long boardId;

    private String name;

    private String statusMapping;

    private Integer wipLimit;

    private Integer sortOrder;

    private String color;

    private LocalDateTime createdAt;
}
