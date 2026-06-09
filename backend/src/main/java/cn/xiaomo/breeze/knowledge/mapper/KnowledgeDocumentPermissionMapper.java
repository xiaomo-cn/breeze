package cn.xiaomo.breeze.knowledge.mapper;

import cn.xiaomo.breeze.knowledge.entity.KnowledgeDocumentPermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeDocumentPermissionMapper extends BaseMapper<KnowledgeDocumentPermission> {

    /** 查询文档的所有权限 */
    List<KnowledgeDocumentPermission> selectByDocumentId(@Param("documentId") Long documentId);

    /** 查询用户对某个文档的权限 */
    KnowledgeDocumentPermission selectByDocAndUser(@Param("documentId") Long documentId,
                                                   @Param("userId") Long userId);
}
