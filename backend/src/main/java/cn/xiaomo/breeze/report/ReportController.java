package cn.xiaomo.breeze.report;

import cn.xiaomo.breeze.report.dto.DailyReportDTO;
import cn.xiaomo.breeze.report.dto.SprintReportDTO;
import cn.xiaomo.breeze.report.dto.WeeklyReportDTO;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final PdfExportService pdfExportService;

    @GetMapping("/daily")
    public ResponseEntity<DailyReportDTO> daily(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.dailyReport(projectId, date));
    }

    @GetMapping("/weekly")
    public ResponseEntity<WeeklyReportDTO> weekly(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(reportService.weeklyReport(projectId, start, end));
    }

    @GetMapping("/sprint/{sprintId}")
    public ResponseEntity<SprintReportDTO> sprint(
            @PathVariable Long projectId,
            @PathVariable Long sprintId) {
        return ResponseEntity.ok(reportService.sprintReport(projectId, sprintId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @PathVariable Long projectId,
            @RequestParam String type,
            @RequestParam String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long sprintId) throws Exception {

        if ("csv".equalsIgnoreCase(format)) {
            byte[] csv = pdfExportService.exportCsv(projectId, type, date, start, end, sprintId);
            return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition",
                    "attachment; filename=" + type + "-report.csv")
                .body(csv);
        }

        byte[] pdf = pdfExportService.exportPdf(projectId, type, date, start, end, sprintId);
        return ResponseEntity.ok()
            .header("Content-Type", "application/pdf")
            .header("Content-Disposition",
                "attachment; filename=" + type + "-report.pdf")
            .body(pdf);
    }
}
