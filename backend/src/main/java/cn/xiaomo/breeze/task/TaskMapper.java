package cn.xiaomo.breeze.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    List<Task> fulltextSearch(@Param("projectId") Long projectId,
                              @Param("query") String query,
                              @Param("limit") int limit);

    /** 查询指定父任务的所有未删除子任务 */
    List<Task> selectByParentId(@Param("parentId") Long parentId);

    /** 批量统计子任务数量（total + done） */
    List<Map<String, Object>> countByParentIds(@Param("parentIds") List<Long> parentIds);
}
