package cn.xiaomo.breeze.knowledge.service;

import cn.xiaomo.breeze.knowledge.entity.KnowledgeDocument;
import cn.xiaomo.breeze.knowledge.mapper.KnowledgeDocumentMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * 知识库检索服务。
 * 将用户问题向量化 → 检索切片 → 按文档聚合 → 相关性过滤 → 权限过滤 → 返回文档及相似度分数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    private final VectorStore vectorStore;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgePermissionService permissionService;

    /** 相关性阈值（cosine similarity），低于此值的文档不返回 */
    private static final double MIN_SIMILARITY = 0.5;

    /**
     * 检索结果：文档 + 最高相似度分数。
     */
    public record RetrievalResult(KnowledgeDocument document, double score) {}

    /**
     * 检索相关文档（切片级检索 → 按文档聚合）。
     *
     * @param query  用户问题
     * @param userId 当前用户 ID（用于权限过滤）
     * @param topK   返回文档数量
     * @return 匹配的文档及相似度分数列表（按分数降序）
     */
    public List<RetrievalResult> retrieve(String query, Long userId, int topK) {
        try {
            // 检索切片（多拿一些以备聚合后仍够 topK）
            List<Document> chunks = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK * 6)
                            .filterExpression("doc_type == \"knowledge_document\"")
                            .build());

            // 按 doc_id 聚合，记录每个文档的最高相似度
            // LinkedHashMap 保持插入顺序（向量检索已按相似度降序）
            Map<Long, Double> docScores = new LinkedHashMap<>();
            for (Document chunk : chunks) {
                Map<String, Object> meta = chunk.getMetadata();
                Object docIdObj = meta.get("doc_id");
                if (docIdObj == null) continue;

                long docId = docIdObj instanceof Number
                        ? ((Number) docIdObj).longValue()
                        : Long.parseLong(docIdObj.toString());

                // 使用向量检索返回的距离/相似度（pgvector cosine 距离越小越相似）
                double score = chunk.getScore() > 0 ? chunk.getScore() : 0;
                docScores.merge(docId, score, Math::max);
            }

            // 按分数过滤（阈值）、按权限过滤，取 topK
            List<RetrievalResult> results = new ArrayList<>();
            for (Map.Entry<Long, Double> entry : docScores.entrySet()) {
                if (results.size() >= topK) break;
                Long docId = entry.getKey();
                double score = entry.getValue();

                // 相关性阈值过滤
                if (score < MIN_SIMILARITY) {
                    log.debug("文档 {} 相似度 {} 低于阈值 {}，跳过", docId, score, MIN_SIMILARITY);
                    continue;
                }

                // 权限过滤
                if (!permissionService.isVisible(docId, userId)) continue;

                KnowledgeDocument doc = documentMapper.selectById(docId);
                if (doc != null && !Boolean.TRUE.equals(doc.getIsDeleted())) {
                    results.add(new RetrievalResult(doc, score));
                }
            }

            log.debug("检索完成: query={}, 匹配切片数={}, 聚合文档数={}, 最终返回={}",
                    query, chunks.size(), docScores.size(), results.size());
            return results;
        } catch (Exception e) {
            log.error("知识库检索失败: query={}", query, e);
            return List.of();
        }
    }
}
