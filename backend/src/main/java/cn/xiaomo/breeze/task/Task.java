package cn.xiaomo.breeze.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 任务实体，映射 tasks 表。
 */
@Data
@TableName("tasks")
public class Task {
    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    private Long projectId;

    /** 父任务 ID（用于子任务层级） */
    private Long parentId;

    /** 任务编号，如 T-42 */
    private String key;

    /** 任务标题 */
    private String title;

    /** 任务描述（Markdown） */
    private String description;

    /** 类型：task / bug / story / epic */
    private String type;

    /** 状态：todo / in_progress / review / done */
    private String status;

    /** 优先级：low / medium / high / critical */
    private String priority;

    /** 指派人用户 ID */
    private Long assigneeId;

    /** 报告人用户 ID */
    private Long reporterId;

    /** 所属迭代 ID */
    private Long sprintId;

    /** 故事点数 */
    private Integer storyPoints;

    /** 预估工时（小时） */
    private BigDecimal estimatedHours;

    /** 已记录工时（小时） */
    private BigDecimal loggedHours;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 解决/完成时间 */
    private LocalDateTime resolvedAt;

    /** 在看板列内的排序序号 */
    private Integer sortOrder;

    /** 看板列 ID */
    private Long kanbanColumnId;

    /** 风险等级：none / low / medium / high */
    private String riskLevel;

    /** 风险原因说明 */
    private String riskReason;

    /** 协作人用户 ID 列表（仅用于 API 传输，不映射数据库列） */
    @TableField(exist = false)
    private List<Long> collaboratorIds;

    /** 软删除标记 */
    private Boolean isDeleted;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
