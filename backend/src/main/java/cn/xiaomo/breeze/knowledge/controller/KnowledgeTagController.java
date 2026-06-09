package cn.xiaomo.breeze.knowledge.controller;

import cn.xiaomo.breeze.knowledge.entity.KnowledgeTag;
import cn.xiaomo.breeze.knowledge.service.KnowledgeTagService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/tags")
@RequiredArgsConstructor
public class KnowledgeTagController {

    private final KnowledgeTagService tagService;

    /** 标签列表 + 自动补全 */
    @GetMapping
    public ResponseEntity<List<KnowledgeTag>> list(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(tagService.list(keyword));
    }
}
