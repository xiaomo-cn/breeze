package cn.xiaomo.breeze.task;

import cn.xiaomo.breeze.ai.service.RiskAssessmentService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TaskEventListener {

    private static final Logger log = LoggerFactory.getLogger(TaskEventListener.class);

    private final VectorStore vectorStore;
    private final RiskAssessmentService riskAssessmentService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskChanged(TaskChangedEvent event) {
        try {
            Task task = event.task();
            String content = task.getTitle() + " "
                + (task.getDescription() != null ? task.getDescription() : "");

            Document doc = new Document(
                task.getId().toString(),
                content,
                Map.of(
                    "task_id", task.getId(),
                    "project_id", task.getProjectId()
                )
            );
            vectorStore.add(List.of(doc));

            log.debug("Embedding updated for task {}", task.getId());

            try {
                riskAssessmentService.assess(task);
            } catch (Exception e) {
                log.error("Async risk assessment failed for task {}", task.getId(), e);
            }
        } catch (Exception e) {
            log.error("Failed to update embedding for task {}", event.task().getId(), e);
        }
    }

    public record TaskChangedEvent(Task task) {}
}
