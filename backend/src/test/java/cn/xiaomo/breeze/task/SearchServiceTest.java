package cn.xiaomo.breeze.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private SearchService searchService;

    private static final Long PROJECT_ID = 1L;

    @Nested
    class Search {

        @Test
        void shouldDoFulltextSearchByDefault() {
            List<Task> tasks = List.of(new Task());
            when(taskMapper.fulltextSearch(PROJECT_ID, "keyword", 20)).thenReturn(tasks);

            SearchService.SearchResult result = searchService.search(PROJECT_ID, "keyword", "fulltext", 20);

            assertThat(result.type()).isEqualTo("fulltext");
            assertThat(result.tasks()).isEqualTo(tasks);
            verifyNoInteractions(vectorStore);
        }

        @Test
        void shouldDoFulltextSearchWhenTypeIsNull() {
            List<Task> tasks = List.of(new Task());
            when(taskMapper.fulltextSearch(PROJECT_ID, "keyword", 20)).thenReturn(tasks);

            SearchService.SearchResult result = searchService.search(PROJECT_ID, "keyword", null, 20);

            assertThat(result.type()).isEqualTo("fulltext");
        }

        @Test
        void shouldDoSemanticSearch() {
            Task task1 = new Task();
            task1.setId(10L);
            task1.setProjectId(PROJECT_ID);
            task1.setTitle("AI query result");

            Document doc = new Document("10", "AI query",
                new HashMap<>(Map.of("task_id", 10L, "project_id", PROJECT_ID)));
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));
            when(taskMapper.selectByIds(List.of(10L)))
                .thenReturn(new ArrayList<>(List.of(task1)));

            SearchService.SearchResult result = searchService.search(
                PROJECT_ID, "AI query", "semantic", 20);

            assertThat(result.type()).isEqualTo("semantic");
            assertThat(result.tasks()).hasSize(1);
            assertThat(result.tasks().get(0).getTitle()).isEqualTo("AI query result");
        }
    }

    @Nested
    class FulltextSearch {

        @Test
        void shouldDelegateToMapper() {
            List<Task> expected = List.of(new Task());
            when(taskMapper.fulltextSearch(PROJECT_ID, "bug", 10)).thenReturn(expected);

            List<Task> result = searchService.fulltextSearch(PROJECT_ID, "bug", 10);

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    class SemanticSearch {

        @Test
        void shouldReturnEmptyWhenNoDocsMatchProject() {
            Document doc = new Document("1", "content",
                new HashMap<>(Map.of("task_id", 1L, "project_id", 999L)));
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

            List<Task> result = searchService.semanticSearch(PROJECT_ID, "query", 15);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldFetchTasksForMatchingDocs() {
            Task task = new Task();
            task.setId(5L);
            task.setProjectId(PROJECT_ID);

            Document doc = new Document("5", "content",
                new HashMap<>(Map.of("task_id", 5L, "project_id", PROJECT_ID)));
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));
            when(taskMapper.selectByIds(List.of(5L)))
                .thenReturn(new ArrayList<>(List.of(task)));

            List<Task> result = searchService.semanticSearch(PROJECT_ID, "dashboard", 15);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(5L);
        }
    }
}
