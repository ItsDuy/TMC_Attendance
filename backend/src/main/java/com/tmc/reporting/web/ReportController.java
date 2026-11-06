package com.tmc.reporting.web;

import com.tmc.reporting.api.MonthlyReportRow;
import com.tmc.reporting.domain.HrExportBatch;
import com.tmc.reporting.service.HrExportService;
import com.tmc.reporting.service.ReportingService;
import com.tmc.security.api.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportingService reporting;
    private final HrExportService exporter;

    public ReportController(ReportingService reporting, HrExportService exporter) {
        this.reporting = reporting;
        this.exporter = exporter;
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EXECUTIVE','ADMIN')")
    public List<MonthlyReportRow> monthly(@RequestParam String period,
                                          @RequestParam(required = false) UUID factoryId) {
        return reporting.monthly(YearMonth.parse(period), factoryId);
    }

    @PostMapping("/exports")
    @PreAuthorize("hasRole('HR')")
    public ExportResponse export(@Valid @RequestBody ExportRequest request,
                                 Authentication authentication) {
        HrExportBatch batch = exporter.export(YearMonth.parse(request.period()),
                request.factoryId(), SecurityUtils.actor(authentication));
        return ExportResponse.from(batch);
    }

    @GetMapping("/exports")
    @PreAuthorize("hasAnyRole('HR','EXECUTIVE','ADMIN')")
    public List<ExportResponse> exports(@RequestParam String period) {
        return exporter.listByPeriod(period).stream()
                .map(ExportResponse::from)
                .toList();
    }

    public record ExportRequest(@NotBlank String period, @NotNull UUID factoryId) {
    }

    public record ExportResponse(UUID id, String period, UUID factoryId, String idempotencyKey,
                                 int recordCount, String checksum, String status, int attempts,
                                 Instant sentAt) {
        static ExportResponse from(HrExportBatch batch) {
            return new ExportResponse(batch.getId(), batch.getPeriod(), batch.getFactoryId(),
                    batch.getIdempotencyKey(), batch.getRecordCount(), batch.getChecksum(),
                    batch.getStatus(), batch.getAttempts(), batch.getSentAt());
        }
    }
}
