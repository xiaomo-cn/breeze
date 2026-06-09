package cn.xiaomo.breeze.knowledge.service;

import cn.xiaomo.breeze.knowledge.entity.KnowledgeTag;
import cn.xiaomo.breeze.knowledge.mapper.KnowledgeTagMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 标签管理服务 */
@Service
@RequiredArgsConstructor
public class KnowledgeTagService {

    private final KnowledgeTagMapper tagMapper;

    /** 标签列表 + 自动补全 */
    public List<KnowledgeTag> list(String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return tagMapper.searchByName(keyword);
        }
        return tagMapper.selectList(new LambdaQueryWrapper<KnowledgeTag>()
                .orderByAsc(KnowledgeTag::getName));
    }

    /** 为文档添加标签（自动创建不存在的标签） */
    public void addTag(Long documentId, String tagName) {
        KnowledgeTag tag = tagMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeTag>().eq(KnowledgeTag::getName, tagName));
        if (tag == null) {
            tag = new KnowledgeTag();
            tag.setName(tagName);
            tagMapper.insert(tag);
        }
        // INSERT IGNORE into knowledge_document_tags
        tagMapper.insertDocumentTag(documentId, tag.getId());
    }

    /** 设置文档标签（全量替换） */
    public void setTags(Long documentId, List<String> tagNames) {
        tagMapper.deleteDocumentTags(documentId);
        if (tagNames != null) {
            for (String name : tagNames) {
                addTag(documentId, name.trim());
            }
        }
    }

    /** 文档的所有标签 */
    public List<KnowledgeTag> getByDocumentId(Long documentId) {
        return tagMapper.selectByDocumentId(documentId);
    }
}
