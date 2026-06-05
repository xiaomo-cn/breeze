package cn.xiaomo.breeze.comment.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class CommentDTO {
    private Long id;
    private Long taskId;
    private Long parentId;
    private Long userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String content;
    private List<CommentDTO> replies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
