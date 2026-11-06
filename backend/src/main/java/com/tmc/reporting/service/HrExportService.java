package com.tmc.reporting.service;

import com.tmc.reporting.api.MonthlyReportRow;
import com.tmc.reporting.api.PayrollAck;
import com.tmc.reporting.api.PayrollBatch;
import com.tmc.reporting.api.PayrollPort;
import com.tmc.reporting.domain.HrExportBatch;
import com.tmc.reporting.repo.HrExportBatchRepository;
import com.tmc.security.api.AuditPort;
import com.tmc.shared.crypto.HmacUtil;
import com.tmc.shared.error.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Exports one period for one factory exactly once (ADR-007, ADR-010). The
 * idempotency key is period + factory, so a retry at 11:59 returns the batch
 * that was already delivered instead of duplicating it.
 */
@Service
public class HrExportService {

    private final ReportingService reporting;
    private final PayrollPort payroll;
    private final HrExportBatchRepository batches;
    private final AuditPort audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public HrExportService(ReportingService reporting, PayrollPort payroll,
                           HrExportBatchRepository batches, AuditPort audit,
                           TransactionTemplate transactions, Clock clock) {
        this.reporting = reporting;
        this.payroll = payroll;
        this.batches = batches;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public HrExportBatch export(YearMonth period, UUID factoryId, String actor) {
        if (factoryId == null) {
            throw DomainException.badRequest("FACTORY_REQUIRED",
                    "An HR export is always scoped to one factory");
        }
        String key = key(period, factoryId);
        Optional<HrExportBatch> existing = batches.findByIdempotencyKey(key);
        if (existing.isPresent() && !existing.get().isFailed()) {
            return existing.get();
        }
        // No batch yet, or the previous attempt FAILED: (re)deliver. The payroll side
        // is idempotent on the key, so a retry can never double-post.
        try {
            return deliver(period, factoryId, key, actor, existing.map(HrExportBatch::getId));
        } catch (DataIntegrityViolationException e) {
            return batches.findByIdempotencyKey(key).orElseThrow(() -> e);
        }
    }

    /** The payroll call runs outside any DB transaction so a slow API holds no connection. */
    private HrExportBatch deliver(YearMonth period, UUID factoryId, String key, String actor,
                                  Optional<UUID> previousId) {
        List<MonthlyReportRow> rows = reporting.monthly(period, factoryId);
        String checksum = checksum(rows);

        PayrollBatch batch = new PayrollBatch(
                period.toString(), factoryId, key, checksum, rows.size(),
                rows.stream()
                        .map(row -> new PayrollBatch.PayrollRow(
                                row.employeeId(), row.employeeCode(), row.daysPresent(),
                                row.checkIns(), row.checkOuts()))
                        .toList());

        PayrollAck ack;
        try {
            ack = payroll.send(batch);
        } catch (RuntimeException e) {
            ack = new PayrollAck(false, null, e.getMessage());
        }
        PayrollAck result = ack;

        return transactions.execute(status -> {
            Instant now = clock.instant();
            HrExportBatch entity;
            if (previousId.isPresent()) {
                entity = batches.findById(previousId.get()).orElseThrow();
                entity.recordAttempt(rows.size(), checksum, result.accepted(), result.detail(), now);
            } else {
                entity = batches.save(HrExportBatch.firstAttempt(
                        period.toString(), factoryId, key, rows.size(), checksum,
                        result.accepted(), result.detail(), now));
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("period", period.toString());
            payload.put("factoryId", factoryId.toString());
            payload.put("records", rows.size());
            payload.put("checksum", checksum);
            payload.put("attempt", entity.getAttempts());
            payload.put("reference", result.reference() == null ? "" : result.reference());
            audit.record(actor, "HR_EXPORT_" + entity.getStatus(), "HrExportBatch",
                    entity.getId().toString(), payload);
            return entity;
        });
    }

    @Transactional(readOnly = true)
    public List<HrExportBatch> listByPeriod(String period) {
        return batches.findByPeriodOrderByCreatedAtDesc(period);
    }

    public static String key(YearMonth period, UUID factoryId) {
        return "HR-" + period + "-" + factoryId;
    }

    private static String checksum(List<MonthlyReportRow> rows) {
        StringBuilder builder = new StringBuilder();
        for (MonthlyReportRow row : rows) {
            builder.append(row.employeeId()).append('|')
                    .append(row.employeeCode()).append('|')
                    .append(row.daysPresent()).append('|')
                    .append(row.checkIns()).append('|')
                    .append(row.checkOuts()).append('\n');
        }
        return HmacUtil.sha256Hex(builder.toString());
    }
}
