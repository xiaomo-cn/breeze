package cn.xiaomo.breeze.comment;

import cn.xiaomo.breeze.comment.dto.CommentDTO;
import cn.xiaomo.breeze.comment.dto.CreateCommentRequest;
import cn.xiaomo.breeze.common.PageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/tasks/{taskId}/comments")
    public ResponseEntity<CommentDTO> create(
            @PathVariable Long taskId,
            @RequestBody CreateCommentRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(commentService.create(taskId, request, userId));
    }

    @GetMapping("/tasks/{taskId}/comments")
    public ResponseEntity<PageDTO<CommentDTO>> list(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(commentService.listByTask(taskId, page, size));
    }

    @PutMapping("/comments/{id}")
    public ResponseEntity<CommentDTO> update(
            @PathVariable Long id,
            @RequestBody CreateCommentRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(commentService.update(id, request.getContent(), userId));
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        commentService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
