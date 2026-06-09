package cn.xiaomo.breeze.knowledge.controller;

import cn.xiaomo.breeze.knowledge.entity.KnowledgeDocument;
import cn.xiaomo.breeze.knowledge.entity.KnowledgeTag;
import cn.xiaomo.breeze.knowledge.service.KnowledgeDocumentService;
import cn.xiaomo.breeze.knowledge.service.KnowledgePermissionService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService documentService;
    private final KnowledgePermissionService permissionService;

    /** 上传文档 */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocument> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long parentFolderId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false, defaultValue = "everyone") String defaultPermission,
            @RequestParam(required = false) List<String> tags,
            Principal principal) throws Exception {
        Long userId = getUserId(principal);
        KnowledgeDocument doc = documentService.upload(
                parentFolderId, title, file.getOriginalFilename(),
                file.getContentType(), file.getSize(), file.getInputStream(),
                description, tags, defaultPermission, userId);
        return ResponseEntity.ok(doc);
    }

    /** 新建文件夹 */
    @PostMapping("/folders")
    public ResponseEntity<KnowledgeDocument> createFolder(
            @RequestParam(required = false) Long parentFolderId,
            @RequestParam String name,
            Principal principal) {
        Long userId = getUserId(principal);
        KnowledgeDocument folder = documentService.createFolder(parentFolderId, name, userId);
        return ResponseEntity.ok(folder);
    }

    /** 当前文件夹内容（网格模式） */
    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocument>> listFolder(
            @RequestParam(required = false) Long parentFolderId) {
        return ResponseEntity.ok(documentService.listFolderContents(parentFolderId));
    }

    /** 文件夹树（树形模式） */
    @GetMapping("/documents/tree")
    public ResponseEntity<List<KnowledgeDocument>> getTree() {
        return ResponseEntity.ok(documentService.getFolderTree());
    }

    /** 搜索 */
    @GetMapping("/documents/search")
    public ResponseEntity<List<KnowledgeDocument>> search(
            @RequestParam(required = false) Long parentFolderId,
            @RequestParam String keyword) {
        return ResponseEntity.ok(documentService.search(parentFolderId, keyword));
    }

    /** 文档详情 */
    @GetMapping("/documents/{id}")
    public ResponseEntity<KnowledgeDocument> getById(@PathVariable Long id) {
        KnowledgeDocument doc = documentService.getById(id);
        return doc != null ? ResponseEntity.ok(doc) : ResponseEntity.notFound().build();
    }

    /** 文档标签 */
    @GetMapping("/documents/{id}/tags")
    public ResponseEntity<List<KnowledgeTag>> getTags(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getTags(id));
    }

    /** 更新文档元数据 */
    @PutMapping("/documents/{id}")
    public ResponseEntity<Void> updateMeta(
            @PathVariable Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) List<String> tags,
            Principal principal) {
        documentService.updateMeta(id, title, description, tags, getUserId(principal));
        return ResponseEntity.ok().build();
    }

    /** 删除文档/文件夹 */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        documentService.delete(id, getUserId(principal));
        return ResponseEntity.ok().build();
    }

    /** 重试向量化 */
    @PostMapping("/documents/{id}/retry-embedding")
    public ResponseEntity<Void> retryEmbedding(@PathVariable Long id, Principal principal) {
        documentService.retryEmbedding(id, getUserId(principal));
        return ResponseEntity.ok().build();
    }

    /** 移动文档/文件夹到指定文件夹 */
    @PutMapping("/documents/{id}/move")
    public ResponseEntity<Void> moveDocument(
            @PathVariable Long id,
            @RequestParam(required = false) Long parentFolderId,
            Principal principal) {
        documentService.moveDocument(id, parentFolderId, getUserId(principal));
        return ResponseEntity.ok().build();
    }

    private Long getUserId(Principal principal) {
        // 通过 SecurityContext 获取用户 ID
        // 简化实现：从 Principal name 解析
        if (principal == null) throw new RuntimeException("未登录");
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            throw new RuntimeException("无法获取用户信息");
        }
    }
}
