package cn.xiaomo.breeze.knowledge.service;

import cn.xiaomo.breeze.attachment.FileStorageService;
import cn.xiaomo.breeze.knowledge.entity.KnowledgeDocument;
import cn.xiaomo.breeze.knowledge.mapper.KnowledgeDocumentMapper;
import cn.xiaomo.breeze.knowledge.parser.DocumentParser;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 知识库 Embedding 服务。
 * <p>
 * 使用 Spring AI ETL 管线：DocumentParser 提取文本 → 自适应切片 → text-embedding-v4 向量化 → vector_store。
 * </p>
 */
@Slf4j
@Service
public class KnowledgeEmbeddingService {

    private final KnowledgeDocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final VectorStore vectorStore;
    private final DocumentParser documentParser;

    public KnowledgeEmbeddingService(
            KnowledgeDocumentMapper documentMapper,
            FileStorageService fileStorageService,
            VectorStore vectorStore,
            DocumentParser documentParser) {
        this.documentMapper = documentMapper;
        this.fileStorageService = fileStorageService;
        this.vectorStore = vectorStore;
        this.documentParser = documentParser;
    }

    @Value("${app.knowledge.splitter.default-chunk-size:500}")
    private int defaultChunkSize;

    @Value("${app.knowledge.splitter.max-chunks-per-doc:100}")
    private int maxChunksPerDoc;

    /**
     * 异步对文档进行向量化。
     */
    @Async
    public void embedDocumentAsync(KnowledgeDocument doc) {
        try {
            doc.setEmbeddingStatus("processing");
            documentMapper.updateById(doc);

            // 1. DocumentParser 提取文本
            String text = extractText(doc);
            if (text == null || text.isBlank()) {
                log.warn("文档 {} 无法提取文本内容", doc.getId());
                doc.setEmbeddingStatus("completed");
                doc.setChunkCount(0);
                documentMapper.updateById(doc);
                return;
            }

            // 1.1 将全文存入 extracted_text，后续问答直接读取
            doc.setExtractedText(text);
            documentMapper.updateById(doc);

            // 2. TokenTextSplitter 按 token 自适应切片
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(defaultChunkSize)
                    .withMaxNumChunks(maxChunksPerDoc)
                    .build();
            // 创建包含基础 metadata 的 Spring AI Document
            Document sourceDoc = new Document(text, Map.of(
                    "doc_id", doc.getId().toString(),
                    "doc_type", "knowledge_document"));
            List<Document> chunks = splitter.split(sourceDoc);

            // 3. 为每个切片补充 metadata 并写入 vector_store
            List<Document> docs = new ArrayList<>();
            int chunkIndex = 0;
            for (Document chunk : chunks) {
                if (chunkIndex >= maxChunksPerDoc) {
                    log.warn("文档 {} 切片数超过上限 {}，截断", doc.getId(), maxChunksPerDoc);
                    break;
                }
                Map<String, Object> chunkMeta = new java.util.HashMap<>(chunk.getMetadata());
                chunkMeta.put("chunk_index", String.valueOf(chunkIndex));
                chunkMeta.put("page_number", String.valueOf(chunkIndex / 2 + 1));
                Document chunkDoc = new Document(
                        UUID.randomUUID().toString(),
                        chunk.getText(),
                        chunkMeta);
                docs.add(chunkDoc);
                chunkIndex++;
            }

            if (!docs.isEmpty()) {
                // 阿里百炼 API 限制每次最多 10 条，需要分批写入
                int batchSize = 10;
                int totalChunks = docs.size();
                log.info("开始分批写入 {} 个切片到 vector_store（每批 {} 个）...", totalChunks, batchSize);
                for (int i = 0; i < totalChunks; i += batchSize) {
                    int end = Math.min(i + batchSize, totalChunks);
                    List<Document> batch = docs.subList(i, end);
                    vectorStore.add(batch);
                    log.info("已写入第 {}/{} 批（{} 个切片）", i / batchSize + 1,
                            (totalChunks + batchSize - 1) / batchSize, batch.size());
                }
                log.info("已成功写入全部 {} 个切片到 vector_store", totalChunks);
            } else {
                log.warn("文档 {} 没有生成任何切片", doc.getId());
            }

            doc.setEmbeddingStatus("completed");
            doc.setChunkCount(docs.size());
            documentMapper.updateById(doc);

            log.info("文档 {} ({}) 向量化完成：{} 个切片", doc.getId(), doc.getTitle(), docs.size());
        } catch (Exception e) {
            log.error("文档 {} 向量化失败: {}", doc.getId(), e.getMessage(), e);
            doc.setEmbeddingStatus("failed");
            documentMapper.updateById(doc);
        }
    }

    /** 删除文档的所有向量 */
    public void deleteVectors(Long documentId) {
        try {
            // 通过 filter 找到所有切片并删除
            var docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("")
                            .filterExpression("doc_id == " + documentId)
                            .topK(maxChunksPerDoc)
                            .build());
            for (Document doc : docs) {
                vectorStore.delete(List.of(doc.getId()));
            }
        } catch (Exception e) {
            log.warn("清理向量失败: documentId={}", documentId, e);
        }
    }

    // ========== 私有方法 ==========

    private String extractText(KnowledgeDocument doc) {
        try (InputStream is = fileStorageService.retrieve(doc.getStorageKey())) {
            return documentParser.parse(is, doc.getFileType(), doc.getFileName());
        } catch (Exception e) {
            log.warn("文档解析失败: {}", doc.getFileName(), e);
            return null;
        }
    }

}
