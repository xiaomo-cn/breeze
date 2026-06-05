package cn.xiaomo.breeze.attachment;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;
    private final AttachmentMapper attachmentMapper;

    @PostMapping("/tasks/{taskId}/attachments")
    public ResponseEntity<AttachmentDTO> upload(
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            AttachmentDTO dto = attachmentService.upload(taskId,
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                file.getContentType(), file.getSize(), file.getInputStream(), userId);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
        }
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public ResponseEntity<List<AttachmentDTO>> list(@PathVariable Long taskId) {
        return ResponseEntity.ok(attachmentService.listByTask(taskId));
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        Attachment att = attachmentMapper.selectById(id);
        if (att == null) {
            return ResponseEntity.notFound().build();
        }

        // S3/MinIO/OSS 模式：302 重定向到预签名 URL
        if (fileStorageService.supportsDirectUrl()) {
            String url = fileStorageService.getUrl(att.getStorageKey(), att.getFileName());
            return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, url)
                .build();
        }

        // Local 模式：后端代理流式下载
        AttachmentService.AttachmentDownload dl = attachmentService.download(id);
        InputStreamResource resource = new InputStreamResource(dl.stream());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + dl.fileName() + "\"")
            .contentType(MediaType.parseMediaType(
                dl.contentType() != null ? dl.contentType() : "application/octet-stream"))
            .body(resource);
    }

    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        attachmentService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
