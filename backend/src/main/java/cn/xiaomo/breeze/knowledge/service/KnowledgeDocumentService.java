package cn.xiaomo.breeze.knowledge.service;

import cn.xiaomo.breeze.attachment.FileStorageService;
import cn.xiaomo.breeze.knowledge.entity.KnowledgeDocument;
import cn.xiaomo.breeze.knowledge.entity.KnowledgeTag;
import cn.xiaomo.breeze.knowledge.mapper.KnowledgeDocumentMapper;
import cn.xiaomo.breeze.knowledge.mapper.KnowledgeTagMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeTagMapper tagMapper;
    private final FileStorageService fileStorageService;
    private final KnowledgeEmbeddingService embeddingService;
    private final KnowledgePermissionService permissionService;
    private final KnowledgeTagService tagService;

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    /** 上传文档到指定文件夹 */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument upload(Long parentFolderId, String title, String originalFileName,
                                    String contentType, long fileSize, InputStream inputStream,
                                    String description, List<String> tagNames,
                                    String defaultPermission, Long userId) {
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过 50MB");
        }

        // 读取全部字节（后续用于哈希计算和文件存储）
        byte[] fileBytes;
        try {
            fileBytes = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败", e);
        }

        // 计算哈希并去重
        String fileHash = computeHash(fileBytes);
        KnowledgeDocument existing = documentMapper.selectByFileHash(fileHash);
        if (existing != null) {
            throw new IllegalArgumentException("该文件已上传：" + existing.getTitle());
        }

        // 保存文件（用字节数组）
        String storageKey = "knowledge/" + userId + "/" + System.currentTimeMillis() + "/" + originalFileName;
        String savedKey = fileStorageService.store(originalFileName, contentType, fileSize,
                new java.io.ByteArrayInputStream(fileBytes));

        // 入库
        String fileType = extractFileType(originalFileName);
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setParentFolderId(parentFolderId);
        doc.setTitle(title != null && !title.isBlank() ? title : originalFileName);
        doc.setDescription(description);
        doc.setFileName(originalFileName);
        doc.setFileType(fileType);
        doc.setFileSize(fileSize);
        doc.setFileHash(fileHash);
        doc.setStorageKey(savedKey);
        doc.setEmbeddingStatus("pending");
        doc.setChunkCount(0);
        doc.setCreatedBy(userId);
        doc.setIsDeleted(false);
        documentMapper.insert(doc);

        // 标签关联
        if (tagNames != null) {
            for (String tagName : tagNames) {
                tagService.addTag(doc.getId(), tagName.trim());
            }
        }

        // 权限
        permissionService.inheritOrSetDefault(doc.getId(), parentFolderId, defaultPermission, userId);

        // 异步向量化
        embeddingService.embedDocumentAsync(doc);

        return doc;
    }

    /** 创建文件夹 */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument createFolder(Long parentFolderId, String name, Long userId) {
        KnowledgeDocument folder = new KnowledgeDocument();
        folder.setParentFolderId(parentFolderId);
        folder.setTitle(name);
        folder.setFileType("folder");
        folder.setFileSize(0L);
        folder.setCreatedBy(userId);
        folder.setIsDeleted(false);
        documentMapper.insert(folder);

        permissionService.inheritOrSetDefault(folder.getId(), parentFolderId, "everyone", userId);
        return folder;
    }

    /** 查询当前文件夹内容（文件夹在前） */
    public List<KnowledgeDocument> listFolderContents(Long parentFolderId) {
        return documentMapper.selectByParentFolder(parentFolderId);
    }

    /** 完整文件夹树 */
    public List<KnowledgeDocument> getFolderTree() {
        return documentMapper.selectFolderTree();
    }

    /** 递归搜索标题 */
    public List<KnowledgeDocument> search(Long parentFolderId, String keyword) {
        return documentMapper.searchByTitle(parentFolderId, keyword);
    }

    /** 文档详情 */
    public KnowledgeDocument getById(Long id) {
        KnowledgeDocument doc = documentMapper.selectById(id);
        if (doc == null || Boolean.TRUE.equals(doc.getIsDeleted())) {
            return null;
        }
        return doc;
    }

    /** 文档标签 */
    public List<KnowledgeTag> getTags(Long documentId) {
        return tagMapper.selectByDocumentId(documentId);
    }

    /** 更新文档元数据（标题、描述、标签） */
    @Transactional(rollbackFor = Exception.class)
    public void updateMeta(Long documentId, String title, String description,
                          List<String> tagNames, Long userId) {
        permissionService.checkManagePermission(documentId, userId);
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) throw new IllegalArgumentException("文档不存在");
        if (title != null) doc.setTitle(title);
        if (description != null) doc.setDescription(description);
        documentMapper.updateById(doc);
        if (tagNames != null) {
            tagService.setTags(documentId, tagNames);
        }
    }

    /** 删除文档 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long documentId, Long userId) {
        permissionService.checkManagePermission(documentId, userId);
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) throw new IllegalArgumentException("文档不存在");

        // 非空文件夹拒绝删除
        if ("folder".equals(doc.getFileType())) {
            List<KnowledgeDocument> children = documentMapper.selectByParentFolder(documentId);
            if (!children.isEmpty()) {
                throw new IllegalArgumentException("该文件夹不为空，请先清空内容");
            }
        }

        // 删除物理文件
        if (doc.getStorageKey() != null) {
            try { fileStorageService.delete(doc.getStorageKey()); }
            catch (Exception e) { log.warn("删除文件失败: {}", doc.getStorageKey(), e); }
        }

        // 软删除
        doc.setIsDeleted(true);
        documentMapper.updateById(doc);

        // 清理向量
        embeddingService.deleteVectors(documentId);
    }

    /** 重试向量化 */
    public void retryEmbedding(Long documentId, Long userId) {
        permissionService.checkManagePermission(documentId, userId);
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) throw new IllegalArgumentException("文档不存在");
        doc.setEmbeddingStatus("pending");
        documentMapper.updateById(doc);
        embeddingService.embedDocumentAsync(doc);
    }

    /** 移动文档/文件夹到指定文件夹 */
    @Transactional(rollbackFor = Exception.class)
    public void moveDocument(Long documentId, Long targetFolderId, Long userId) {
        permissionService.checkManagePermission(documentId, userId);

        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) throw new IllegalArgumentException("文档不存在");

        // 不能移到自身
        if (targetFolderId != null && targetFolderId.equals(documentId)) {
            throw new IllegalArgumentException("不能将文件夹移动到自身");
        }

        // 目标必须是文件夹
        if (targetFolderId != null) {
            KnowledgeDocument target = documentMapper.selectById(targetFolderId);
            if (target == null) throw new IllegalArgumentException("目标文件夹不存在");
            if (!"folder".equals(target.getFileType())) {
                throw new IllegalArgumentException("只能将文档移动到文件夹中");
            }
            // 不能将文件夹移到自身的子文件夹中
            if ("folder".equals(doc.getFileType()) && isDescendant(documentId, targetFolderId)) {
                throw new IllegalArgumentException("不能将文件夹移动到自身或其子文件夹中");
            }
        }

        // 已在目标文件夹中，无需移动
        Long currentParent = doc.getParentFolderId();
        if ((currentParent == null && targetFolderId == null)
                || (currentParent != null && currentParent.equals(targetFolderId))) {
            return;
        }

        doc.setParentFolderId(targetFolderId);
        documentMapper.updateById(doc);
    }

    // ========== 私有方法 ==========

    /** 检查 ancestorId 是否是 descendantId 的祖先（沿 parent_folder_id 链向上查找） */
    private boolean isDescendant(Long ancestorId, Long descendantId) {
        Long currentId = descendantId;
        int safety = 0;
        while (currentId != null && safety < 100) {
            if (currentId.equals(ancestorId)) return true;
            KnowledgeDocument current = documentMapper.selectById(currentId);
            currentId = current != null ? current.getParentFolderId() : null;
            safety++;
        }
        return false;
    }

    private String extractFileType(String fileName) {
        if (fileName == null) return "unknown";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "docx";
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return "xlsx";
        if (lower.endsWith(".pptx") || lower.endsWith(".ppt")) return "pptx";
        if (lower.endsWith(".md")) return "md";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpg";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".csv")) return "csv";
        return "unknown";
    }

    private String computeHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算文件哈希失败", e);
        }
    }
}
