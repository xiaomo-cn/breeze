package cn.xiaomo.breeze.knowledge.mapper;

import cn.xiaomo.breeze.knowledge.entity.KnowledgeDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /** 查询指定文件夹下的所有子项（文件夹在前，文件在后，按更新时间倒序） */
    List<KnowledgeDocument> selectByParentFolder(@Param("parentFolderId") Long parentFolderId);

    /** 递归搜索当前目录及所有子目录中匹配标题的文档 */
    List<KnowledgeDocument> searchByTitle(@Param("parentFolderId") Long parentFolderId,
                                          @Param("keyword") String keyword);

    /** 查询完整文件夹树（从根目录开始） */
    List<KnowledgeDocument> selectFolderTree();

    /** 按文件哈希查找（去重用） */
    KnowledgeDocument selectByFileHash(@Param("fileHash") String fileHash);
}
