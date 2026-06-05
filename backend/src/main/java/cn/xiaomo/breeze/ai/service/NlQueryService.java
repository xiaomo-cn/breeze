package cn.xiaomo.breeze.ai.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 自然语言查询服务 — 将自然语言问题转换为 PostgreSQL SQL 并安全执行。
 */
@Service
@RequiredArgsConstructor
public class NlQueryService {

    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 禁止的 DDL/DML 关键字，防止 SQL 注入和误操作。
     */
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE",
        "CREATE", "REPLACE", "MERGE", "GRANT", "REVOKE"
    );

    /**
     * 自然语言查询（SSE 流式生成 SQL）。
     *
     * @param question  自然语言问题
     * @param projectId 项目 ID
     * @return SSE 格式的流式 SQL 生成结果
     */
    public Flux<ServerSentEvent<String>> query(String question, Long projectId) {
        String sqlPrompt = buildSqlPrompt(question, projectId);

        return chatClient.prompt()
            .user(sqlPrompt)
            .stream()
            .content()
            .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
    }

    /**
     * 构建 AI 提示词，要求生成 SQL。
     */
    private String buildSqlPrompt(String question, Long projectId) {
        return promptTemplateService.render("sql-query-prompt", Map.of(
                "question", question,
                "projectId", projectId.toString()
        ));
    }

    /**
     * 安全执行 SQL 查询。
     *
     * @param sql SQL 语句（仅允许 SELECT）
     * @return 包含 columns、rows、total 的结果 Map
     */
    public Map<String, Object> executeSql(String sql) {
        // 安全检查：转大写后统一检查，防止大小写混合绕过
        String upperSql = sql.toUpperCase();
        for (String kw : FORBIDDEN_KEYWORDS) {
            // FORBIDDEN_KEYWORDS 中的值已是大写，直接检查
            if (upperSql.contains(kw)) {
                throw new IllegalArgumentException("禁止的 SQL 操作: " + kw);
            }
        }
        // 去除注释后检查是否以 SELECT 开头，防止 /*!50000 Select */ 绕过
        String strippedSql = upperSql.replaceAll("/\\*.*?\\*/", "").trim();
        if (!strippedSql.startsWith("SELECT")) {
            throw new IllegalArgumentException("仅允许 SELECT 查询");
        }

        // 强制 LIMIT 防止返回过多数据
        String safeSql = sql.trim();
        if (!safeSql.toUpperCase().contains("LIMIT")) {
            safeSql += " LIMIT 100";
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(safeSql);

        List<String> columns = rows.isEmpty() ? List.of()
            : new ArrayList<>(rows.get(0).keySet());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("total", rows.size());
        return result;
    }
}
