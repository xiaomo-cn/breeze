package cn.xiaomo.breeze.knowledge.mapper;

import cn.xiaomo.breeze.knowledge.entity.KnowledgeTag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeTagMapper extends BaseMapper<KnowledgeTag> {

    /** 按名称模糊查找（自动补全用） */
    List<KnowledgeTag> searchByName(@Param("keyword") String keyword);

    /** 查找文档的所有标签 */
    List<KnowledgeTag> selectByDocumentId(@Param("documentId") Long documentId);

    /** 插入文档-标签关联 */
    @Insert("INSERT INTO knowledge_document_tags (document_id, tag_id) VALUES (#{documentId}, #{tagId}) ON CONFLICT DO NOTHING")
    void insertDocumentTag(@Param("documentId") Long documentId, @Param("tagId") Long tagId);

    /** 删除文档的所有标签关联 */
    @Delete("DELETE FROM knowledge_document_tags WHERE document_id = #{documentId}")
    void deleteDocumentTags(@Param("documentId") Long documentId);
}
