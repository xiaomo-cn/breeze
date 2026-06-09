package cn.xiaomo.breeze.knowledge.controller;

import cn.xiaomo.breeze.attachment.FileStorageService;
import cn.xiaomo.breeze.knowledge.entity.KnowledgeDocument;
import cn.xiaomo.breeze.knowledge.service.KnowledgeDocumentService;
import cn.xiaomo.breeze.knowledge.service.KnowledgePermissionService;
import java.io.InputStream;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/files")
@RequiredArgsConstructor
public class KnowledgeFileController {

    private final KnowledgeDocumentService documentService;
    private final KnowledgePermissionService permissionService;
    private final FileStorageService fileStorageService;

    /** 文件类型 → MIME 映射 */
    private static final Map<String, MediaType> MIME_TYPES = Map.ofEntries(
            Map.entry("pdf", MediaType.APPLICATION_PDF),
            Map.entry("png", MediaType.IMAGE_PNG),
            Map.entry("jpg", MediaType.IMAGE_JPEG),
            Map.entry("jpeg", MediaType.IMAGE_JPEG),
            Map.entry("gif", MediaType.IMAGE_GIF),
            Map.entry("webp", MediaType.valueOf("image/webp")),
            Map.entry("md", MediaType.TEXT_PLAIN),
            Map.entry("txt", MediaType.TEXT_PLAIN),
            Map.entry("html", MediaType.TEXT_HTML),
            Map.entry("csv", new MediaType("text", "csv"))
    );

    /** 文件预览/下载（需权限校验） */
    @GetMapping("/{documentId}")
    public ResponseEntity<InputStreamResource> getFile(@PathVariable Long documentId,
                                                        Principal principal) {
        KnowledgeDocument doc = documentService.getById(documentId);
        if (doc == null || doc.getStorageKey() == null) {
            return ResponseEntity.notFound().build();
        }
        Long userId = getUserId(principal);
        if (!permissionService.isVisible(documentId, userId)) {
            return ResponseEntity.status(403).build();
        }

        InputStream is = fileStorageService.retrieve(doc.getStorageKey());
        MediaType contentType = MIME_TYPES.getOrDefault(doc.getFileType(), MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + doc.getFileName() + "\"")
                .contentType(contentType)
                .body(new InputStreamResource(is));
    }

    private Long getUserId(Principal principal) {
        if (principal == null) throw new RuntimeException("未登录");
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            throw new RuntimeException("无法获取用户信息");
        }
    }
}
