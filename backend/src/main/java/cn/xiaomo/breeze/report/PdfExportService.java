package cn.xiaomo.breeze.report;

import cn.xiaomo.breeze.report.dto.DailyReportDTO;
import cn.xiaomo.breeze.report.dto.SprintReportDTO;
import cn.xiaomo.breeze.report.dto.WeeklyReportDTO;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

/**
 * PDF/CSV 导出服务
 * 使用 Thymeleaf 模板引擎 + Flying Saucer 将报告渲染为 PDF
 */
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final ReportService reportService;
    private final TemplateEngine templateEngine;

    /**
     * 导出报告为 PDF
     *
     * @param projectId 项目 ID
     * @param type      报告类型：daily / weekly / sprint
     * @param date      日报日期（仅 daily 类型使用）
     * @param start     周报开始日期（仅 weekly 类型使用）
     * @param end       周报结束日期（仅 weekly 类型使用）
     * @param sprintId  Sprint ID（仅 sprint 类型使用）
     * @return PDF 字节数组
     */
    public byte[] exportPdf(Long projectId, String type, LocalDate date,
                            LocalDate start, LocalDate end, Long sprintId) throws Exception {
        String html = switch (type) {
            case "daily" -> renderDailyHtml(projectId, date != null ? date : LocalDate.now());
            case "weekly" -> renderWeeklyHtml(projectId, start, end);
            case "sprint" -> renderSprintHtml(projectId, sprintId);
            default -> throw new IllegalArgumentException("Unknown report type: " + type);
        };

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(os);
            return os.toByteArray();
        }
    }

    /**
     * 导出报告为 CSV
     *
     * @param projectId 项目 ID
     * @param type      报告类型：daily / weekly / sprint
     * @param date      日报日期（仅 daily 类型使用）
     * @param start     周报开始日期（仅 weekly 类型使用）
     * @param end       周报结束日期（仅 weekly 类型使用）
     * @param sprintId  Sprint ID（仅 sprint 类型使用）
     * @return CSV 字节数组（UTF-8 BOM）
     */
    public byte[] exportCsv(Long projectId, String type, LocalDate date,
                            LocalDate start, LocalDate end, Long sprintId) {
        StringBuilder sb = new StringBuilder();
        sb.append('﻿'); // BOM for Excel UTF-8

        switch (type) {
            case "daily" -> {
                DailyReportDTO dto = reportService.dailyReport(projectId,
                    date != null ? date : LocalDate.now());
                sb.append("类型,编号,标题,状态,优先级,负责人\n");
                for (var t : dto.getCompletedTasks()) {
                    sb.append(String.format("已完成,%s,%s,%s,%s,%s\n",
                        t.getKey(), csvEscape(t.getTitle()), t.getStatus(), t.getPriority(),
                        t.getAssigneeName() != null ? t.getAssigneeName() : ""));
                }
                for (var t : dto.getInProgressTasks()) {
                    sb.append(String.format("进行中,%s,%s,%s,%s,%s\n",
                        t.getKey(), csvEscape(t.getTitle()), t.getStatus(), t.getPriority(),
                        t.getAssigneeName() != null ? t.getAssigneeName() : ""));
                }
                for (var t : dto.getBlockedTasks()) {
                    sb.append(String.format("阻塞,%s,%s,%s,%s,%s\n",
                        t.getKey(), csvEscape(t.getTitle()), t.getStatus(), t.getPriority(),
                        t.getAssigneeName() != null ? t.getAssigneeName() : ""));
                }
            }
            case "weekly" -> {
                WeeklyReportDTO dto = reportService.weeklyReport(projectId, start, end);
                sb.append("成员,完成数,创建数\n");
                for (var c : dto.getContributions()) {
                    sb.append(String.format("%s,%d,%d\n",
                        c.getUserName(), c.getCompleted(), c.getCreated()));
                }
            }
            case "sprint" -> {
                SprintReportDTO dto = reportService.sprintReport(projectId, sprintId);
                sb.append("成员,完成数,创建数\n");
                for (var c : dto.getContributions()) {
                    sb.append(String.format("%s,%d,%d\n",
                        c.getUserName(), c.getCompleted(), c.getCreated()));
                }
            }
            default -> { /* 原代码无 else 分支，保留静默忽略行为 */ }
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String renderDailyHtml(Long projectId, LocalDate date) {
        DailyReportDTO dto = reportService.dailyReport(projectId, date);
        Context ctx = new Context();
        ctx.setVariable("report", dto);
        return templateEngine.process("report-daily", ctx);
    }

    private String renderWeeklyHtml(Long projectId, LocalDate start, LocalDate end) {
        WeeklyReportDTO dto = reportService.weeklyReport(projectId, start, end);
        Context ctx = new Context();
        ctx.setVariable("report", dto);
        return templateEngine.process("report-weekly", ctx);
    }

    private String renderSprintHtml(Long projectId, Long sprintId) {
        SprintReportDTO dto = reportService.sprintReport(projectId, sprintId);
        Context ctx = new Context();
        ctx.setVariable("report", dto);
        return templateEngine.process("report-sprint", ctx);
    }

    /**
     * CSV 字段转义：含逗号、双引号或换行的字段用双引号包裹
     */
    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
