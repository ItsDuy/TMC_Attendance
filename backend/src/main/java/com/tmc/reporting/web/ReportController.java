package com.tmc.reporting.web;

import com.tmc.identity.api.AccessScopeService;
import com.tmc.reporting.api.MonthlyReportRow;
import com.tmc.reporting.domain.HrExportBatch;
import com.tmc.reporting.service.HrExportService;
import com.tmc.reporting.service.ManualEntryReviewService;
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
    private final AccessScopeService scopes;
    private final ManualEntryReviewService manualReview;

    public ReportController(ReportingService reporting, HrExportService exporter, AccessScopeService scopes,
                            ManualEntryReviewService manualReview) {
        this.reporting = reporting;
        this.exporter = exporter;
        this.scopes = scopes;
        this.manualReview = manualReview;
    }

    /** Named, per-employee report. A supervisor sees their own team only; executives use /summary. */
    @GetMapping("/attendance")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','ADMIN')")
    public List<MonthlyReportRow> monthly(@RequestParam String period,
                                          @RequestParam(required = false) UUID factoryId,
                                          Authentication authentication) {
        return reporting.monthly(YearMonth.parse(period), factoryId,
                scopes.scopeFor(SecurityUtils.user(authentication)));
    }

    /** Manual entries recorded by guards this month, for HR to review. */
    @GetMapping("/manual-entries")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public List<ManualEntryReviewService.ManualEntryLine> manualEntries(@RequestParam String period) {
        return manualReview.forMonth(YearMonth.parse(period));
    }

    /** Company-level numbers per factory, without names. */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('EXECUTIVE','HR','ADMIN')")
    public List<ReportingService.FactorySummary> summary(@RequestParam String period) {
        return reporting.summary(YearMonth.parse(period));
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
