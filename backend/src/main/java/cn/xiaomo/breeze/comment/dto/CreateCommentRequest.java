package cn.xiaomo.breeze.comment.dto;

import lombok.Data;

@Data
public class CreateCommentRequest {
    private String content;
    private Long parentId;
}
