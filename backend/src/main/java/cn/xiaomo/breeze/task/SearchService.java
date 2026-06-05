package cn.xiaomo.breeze.task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final TaskMapper taskMapper;
    private final VectorStore vectorStore;

    public List<Task> fulltextSearch(Long projectId, String query, int limit) {
        return taskMapper.fulltextSearch(projectId, query, limit);
    }

    public List<Task> semanticSearch(Long projectId, String query, int limit) {
        int fetchLimit = Math.max(limit * 3, 30);

        var docs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(fetchLimit)
                .build()
        );

        List<Long> taskIds = docs.stream()
            .filter(doc -> {
                Object pid = doc.getMetadata().get("project_id");
                return pid instanceof Number
                    && ((Number) pid).longValue() == projectId.longValue();
            })
            .map(doc -> {
                Object tid = doc.getMetadata().get("task_id");
                return tid instanceof Number ? ((Number) tid).longValue() : null;
            })
            .filter(id -> id != null)
            .limit(limit)
            .toList();

        if (taskIds.isEmpty()) {
            return List.of();
        }

        List<Task> tasks = taskMapper.selectByIds(taskIds);

        // 保持相似度排序
        Map<Long, Integer> orderMap = new LinkedHashMap<>();
        for (int i = 0; i < taskIds.size(); i++) {
            orderMap.putIfAbsent(taskIds.get(i), i);
        }
        tasks.sort((a, b) -> {
            int oa = orderMap.getOrDefault(a.getId(), Integer.MAX_VALUE);
            int ob = orderMap.getOrDefault(b.getId(), Integer.MAX_VALUE);
            return Integer.compare(oa, ob);
        });

        return tasks;
    }

    public SearchResult search(Long projectId, String query, String type, int limit) {
        if ("semantic".equals(type)) {
            return new SearchResult(semanticSearch(projectId, query, limit), "semantic");
        }
        return new SearchResult(fulltextSearch(projectId, query, limit), "fulltext");
    }

    public record SearchResult(List<Task> tasks, String type) {}
}
