package cn.xiaomo.breeze.board;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("kanban_boards")
public class KanbanBoard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String name;

    private Boolean isDefault;

    private LocalDateTime createdAt;
}
