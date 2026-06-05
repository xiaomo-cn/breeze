package cn.xiaomo.breeze.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 职务/岗位定义实体。
 */
@Data
@TableName("positions")
public class Position {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 职务名称，如：前端开发、后端开发 */
    private String name;

    /** Ant Design Tag 颜色值 */
    private String color;

    /** 排序序号 */
    private Integer sortOrder;

    private LocalDateTime createdAt;
}
