package cn.xiaomo.breeze.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 项目实体，映射 projects 表。
 */
@Data
@TableName("projects")
public class Project {
    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    private String name;

    /** 项目键，用于任务编号前缀，如 "PM" */
    private String key;

    /** 项目描述 */
    private String description;

    /** 项目图标 URL */
    private String iconUrl;

    /** 状态：active / archived */
    private String status;

    /** 可见性：private / public */
    private String visibility;

    /** 项目负责人（Owner）用户 ID */
    private Long ownerId;

    /** 计划开始日期 */
    private LocalDate startDate;

    /** 计划结束日期 */
    private LocalDate endDate;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
