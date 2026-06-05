package cn.xiaomo.breeze.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.xiaomo.breeze.ai.dto.AiChatRequest;
import cn.xiaomo.breeze.ai.entity.AiConversation;
import cn.xiaomo.breeze.ai.entity.AiMessage;
import cn.xiaomo.breeze.ai.entity.AiToolExecution;
import cn.xiaomo.breeze.ai.entity.PendingToolAction;
import cn.xiaomo.breeze.ai.mapper.AiConversationMapper;
import cn.xiaomo.breeze.ai.mapper.AiMessageMapper;
import cn.xiaomo.breeze.ai.mapper.AiToolExecutionMapper;
import cn.xiaomo.breeze.ai.mapper.PendingToolActionMapper;
import cn.xiaomo.breeze.ai.entity.AiReport;
import cn.xiaomo.breeze.ai.service.AiAgentService;
import cn.xiaomo.breeze.ai.service.AiReportService;
import cn.xiaomo.breeze.ai.service.BreakdownService;
import cn.xiaomo.breeze.ai.service.NlQueryService;
import cn.xiaomo.breeze.ai.service.RiskAssessmentService;
import cn.xiaomo.breeze.ai.service.SchedulingService;
import cn.xiaomo.breeze.project.ProjectMember;
import cn.xiaomo.breeze.project.ProjectMemberMapper;
import cn.xiaomo.breeze.task.Task;
import cn.xiaomo.breeze.task.TaskMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAgentService aiAgentService;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiToolExecutionMapper toolExecutionMapper;
    private final TaskMapper taskMapper;
    private final RiskAssessmentService riskAssessmentService;
    private final AiReportService aiReportService;
    private final BreakdownService breakdownService;
    private final SchedulingService schedulingService;
    private final NlQueryService nlQueryService;
    private final PendingToolActionMapper pendingActionMapper;
    private final cn.xiaomo.breeze.task.TaskService taskService;
    private final ProjectMemberMapper memberMapper;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody AiChatRequest request,
                             Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return aiAgentService.streamChat(
            request.projectId(), userId, request.message(), request.conversationId());
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<Map<String, Object>>> listConversations(
            @RequestParam Long projectId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<AiConversation> convs = conversationMapper.selectList(
            new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .eq(AiConversation::getProjectId, projectId)
                .orderByDesc(AiConversation::getUpdatedAt));

        List<Map<String, Object>> result = convs.stream().map(c -> {
            // 获取消息数量
            Long messageCount = messageMapper.selectCount(
                new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getConversationId, c.getId()));
            // 获取最后一条消息
            List<AiMessage> lastMsgs = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getConversationId, c.getId())
                    .orderByDesc(AiMessage::getCreatedAt)
                    .last("LIMIT 1"));
            String lastMessage = lastMsgs.isEmpty() ? null
                : (lastMsgs.get(0).getContent().length() > 80
                    ? lastMsgs.get(0).getContent().substring(0, 80) + "..."
                    : lastMsgs.get(0).getContent());

            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("title", c.getTitle());
            m.put("model", c.getModel());
            m.put("messageCount", messageCount);
            m.put("lastMessage", lastMessage);
            m.put("createdAt", c.getCreatedAt());
            m.put("updatedAt", c.getUpdatedAt());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        AiConversation conv = conversationMapper.selectById(id);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        // 先删工具执行记录，再删消息，最后删对话
        toolExecutionMapper.delete(new LambdaQueryWrapper<AiToolExecution>()
            .eq(AiToolExecution::getConversationId, id));
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>()
            .eq(AiMessage::getConversationId, id));
        conversationMapper.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<AiMessage>> getMessages(@PathVariable Long id,
                                                         Authentication auth,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        Long userId = (Long) auth.getPrincipal();
        AiConversation conv = conversationMapper.selectById(id);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        List<AiMessage> messages = messageMapper.selectList(
            new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, id)
                .orderByAsc(AiMessage::getCreatedAt)
                .last("LIMIT " + size + " OFFSET " + (Math.max(1, page) - 1) * size));
        return ResponseEntity.ok(messages);
    }

    /**
     * 获取对话关联的工具执行记录。
     */
    @GetMapping("/conversations/{id}/tool-executions")
    public ResponseEntity<List<AiToolExecution>> getToolExecutions(
            @PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        AiConversation conv = conversationMapper.selectById(id);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        List<AiToolExecution> executions = toolExecutionMapper.selectList(
            new LambdaQueryWrapper<AiToolExecution>()
                .eq(AiToolExecution::getConversationId, id)
                .orderByDesc(AiToolExecution::getCreatedAt)
                .last("LIMIT 20"));
        return ResponseEntity.ok(executions);
    }

    /**
     * 评估单个任务的风险等级。
     */
    @PostMapping("/risks/{taskId}")
    public ResponseEntity<Map<String, String>> assessTask(@PathVariable Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        riskAssessmentService.assess(task);
        return ResponseEntity.ok(Map.of(
            "riskLevel", task.getRiskLevel() != null ? task.getRiskLevel() : "",
            "riskReason", task.getRiskReason() != null ? task.getRiskReason() : ""));
    }

    /**
     * 评估项目下所有活跃任务的风险。
     */
    @PostMapping("/risks/project/{projectId}")
    public ResponseEntity<Map<String, Object>> assessProject(@PathVariable Long projectId) {
        riskAssessmentService.assessProject(projectId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * 获取项目的风险概览（按风险等级分组）。
     * 路由为 /api/v1/ai/projects/{projectId}/risks，避免与 ProjectController 的 /api/v1/projects 冲突。
     */
    @GetMapping("/projects/{projectId}/risks")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getProjectRisks(
            @PathVariable Long projectId) {
        return ResponseEntity.ok(riskAssessmentService.getProjectRisks(projectId));
    }

    /**
     * AI 报告生成。生成后自动保存到 ai_reports 表。
     */
    @PostMapping("/reports/generate")
    public ResponseEntity<Map<String, Object>> generateReport(
            @RequestParam String type,
            @RequestParam Long projectId) {
        AiReport report = aiReportService.generate(type, projectId);
        Map<String, Object> result = new HashMap<>();
        result.put("id", report.getId());
        result.put("title", report.getTitle());
        result.put("type", report.getType());
        result.put("markdown", report.getContent());
        result.put("generatedAt", report.getGeneratedAt().toString());
        return ResponseEntity.ok(result);
    }

    /**
     * 查询项目下指定类型的历史 AI 报告列表。
     */
    @GetMapping("/reports")
    public ResponseEntity<List<AiReport>> listReports(
            @RequestParam Long projectId,
            @RequestParam String type) {
        return ResponseEntity.ok(aiReportService.listByProjectAndType(projectId, type));
    }

    /**
     * 按 ID 获取单个 AI 报告。
     */
    @GetMapping("/reports/{id}")
    public ResponseEntity<AiReport> getReport(@PathVariable Long id) {
        AiReport report = aiReportService.getById(id);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(report);
    }

    /**
     * AI 任务拆解 — 流式生成子任务 JSON。
     */
    @PostMapping(value = "/breakdown/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> breakdown(@PathVariable Long taskId) {
        return breakdownService.generateBreakdown(taskId);
    }

    /**
     * 确认拆解结果，批量创建子任务。
     */
    @PostMapping("/breakdown/{taskId}/confirm")
    public ResponseEntity<List<Task>> confirmBreakdown(
            @PathVariable Long taskId,
            @RequestBody List<Map<String, Object>> subtasks,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(breakdownService.batchCreate(taskId, subtasks, userId));
    }

    /**
     * 智能排期建议 — 为指定 Sprint 生成 AI 排期方案。
     */
    @PostMapping("/suggestions/scheduling/{sprintId}")
    public ResponseEntity<List<Map<String, Object>>> suggestScheduling(
            @PathVariable Long sprintId) {
        return ResponseEntity.ok(schedulingService.suggestScheduling(sprintId));
    }

    /**
     * 自然语言查询（SSE 流式）— 将自然语言问题转为 PostgreSQL SQL。
     */
    @PostMapping(value = "/nl-query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> nlQuery(@RequestBody Map<String, Object> body,
                                 Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String question = (String) body.get("question");
        Long projectId = body.get("projectId") != null
            ? Long.valueOf(body.get("projectId").toString()) : null;
        if (question == null || projectId == null) {
            return Flux.just(ServerSentEvent.<String>builder()
                .data("{\"error\":\"question and projectId required\"}").build());
        }
        if (!isProjectMember(projectId, userId)) {
            return Flux.just(ServerSentEvent.<String>builder()
                .data("{\"error\":\"无权访问该项目\"}").build());
        }
        return nlQueryService.query(question, projectId);
    }

    /**
     * 安全执行自然语言查询生成的 SQL。
     */
    @PostMapping("/nl-query/execute")
    public ResponseEntity<Map<String, Object>> executeNlQuery(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String sql = body.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sql required"));
        }
        // 验证请求中包含 projectId 且用户是该项目的成员
        String projectIdStr = body.get("projectId");
        if (projectIdStr != null && !projectIdStr.isBlank()) {
            Long projectId = Long.valueOf(projectIdStr);
            if (!isProjectMember(projectId, userId)) {
                return ResponseEntity.status(403)
                    .body(Map.of("error", "无权访问该项目"));
            }
        }
        try {
            return ResponseEntity.ok(nlQueryService.executeSql(sql));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 确认待执行的工具操作。
     */
    @PostMapping("/confirm-tool/{pendingId}")
    public ResponseEntity<Map<String, Object>> confirmTool(@PathVariable Long pendingId,
                                                            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        PendingToolAction pending = pendingActionMapper.selectById(pendingId);
        if (pending == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"pending".equals(pending.getStatus())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "操作已过期或已处理"));
        }

        try {
            // 执行实际操作（目前仅支持 create_task）
            String result = executePendingAction(pending, userId);
            pending.setStatus("confirmed");
            pending.setResolvedAt(java.time.LocalDateTime.now());
            pendingActionMapper.updateById(pending);
            return ResponseEntity.ok(Map.of("status", "confirmed", "result", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 拒绝待执行的工具操作。
     */
    @PostMapping("/reject-tool/{pendingId}")
    public ResponseEntity<Map<String, Object>> rejectTool(@PathVariable Long pendingId,
                                                           Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        PendingToolAction pending = pendingActionMapper.selectById(pendingId);
        if (pending == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"pending".equals(pending.getStatus())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "操作已过期或已处理"));
        }
        pending.setStatus("rejected");
        pending.setResolvedAt(java.time.LocalDateTime.now());
        pendingActionMapper.updateById(pending);
        return ResponseEntity.ok(Map.of("status", "rejected"));
    }

    /**
     * 执行待确认的操作。
     */
    private String executePendingAction(PendingToolAction pending, Long userId) {
        Map<String, Object> params = pending.getParamsJson();
        if ("create_task".equals(pending.getToolName())) {
            cn.xiaomo.breeze.task.Task task = new cn.xiaomo.breeze.task.Task();
            task.setTitle((String) params.get("title"));
            task.setDescription((String) params.getOrDefault("description", ""));
            task.setType((String) params.getOrDefault("type", "task"));
            task.setPriority((String) params.getOrDefault("priority", "medium"));
            if (params.get("assigneeId") != null) {
                task.setAssigneeId(Long.valueOf(params.get("assigneeId").toString()));
            }
            if (params.get("dueDate") != null) {
                task.setDueDate(java.time.LocalDate.parse(params.get("dueDate").toString()));
            }
            Long projectId = Long.valueOf(params.get("projectId").toString());
            cn.xiaomo.breeze.task.Task created = taskService.create(projectId, task, userId);
            return "任务 " + created.getKey() + " 已创建";
        }
        return "操作已执行";
    }

    /**
     * 检查用户是否为项目成员。
     */
    private boolean isProjectMember(Long projectId, Long userId) {
        return memberMapper.exists(new LambdaQueryWrapper<ProjectMember>()
            .eq(ProjectMember::getProjectId, projectId)
            .eq(ProjectMember::getUserId, userId));
    }
}
