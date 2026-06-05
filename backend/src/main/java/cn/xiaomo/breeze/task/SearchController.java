package cn.xiaomo.breeze.task;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/projects/{pid}/search")
    public ResponseEntity<Map<String, Object>> search(
            @PathVariable Long pid,
            @RequestParam String q,
            @RequestParam(defaultValue = "fulltext") String type,
            @RequestParam(defaultValue = "20") int limit) {
        var result = searchService.search(pid, q, type, limit);
        return ResponseEntity.ok(Map.of(
            "query", q,
            "type", result.type(),
            "tasks", result.tasks()
        ));
    }
}
