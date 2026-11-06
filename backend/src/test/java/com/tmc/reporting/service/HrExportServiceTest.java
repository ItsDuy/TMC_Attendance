package com.tmc.reporting.service;

import com.tmc.reporting.api.PayrollAck;
import com.tmc.reporting.api.PayrollBatch;
import com.tmc.reporting.api.PayrollPort;
import com.tmc.reporting.domain.HrExportBatch;
import com.tmc.reporting.repo.HrExportBatchRepository;
import com.tmc.security.api.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HrExportServiceTest {

    private final YearMonth period = YearMonth.of(2026, 9);
    private final UUID factoryId = UUID.randomUUID();
    private final String key = HrExportService.key(period, factoryId);
    private final Instant now = Instant.parse("2026-10-01T06:00:00Z");

    private ReportingService reporting;
    private PayrollPort payroll;
    private HrExportBatchRepository batches;
    private HrExportService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        reporting = mock(ReportingService.class);
        payroll = mock(PayrollPort.class);
        batches = mock(HrExportBatchRepository.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        when(reporting.monthly(period, factoryId)).thenReturn(List.of());
        when(batches.save(any(HrExportBatch.class))).thenAnswer(inv -> {
            HrExportBatch saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        service = new HrExportService(reporting, payroll, batches, mock(AuditPort.class), tx,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void failedFirstAttemptIsStoredWithItsError() {
        when(batches.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(payroll.send(any())).thenReturn(new PayrollAck(false, null, "HR API down"));

        HrExportBatch batch = service.export(period, factoryId, "scheduler");

        assertThat(batch.getStatus()).isEqualTo("FAILED");
        assertThat(batch.getLastError()).isEqualTo("HR API down");
        assertThat(batch.getAttempts()).isEqualTo(1);
    }

    @Test
    void failedBatchIsRetriedAndBecomesSent() {
        HrExportBatch failed = HrExportBatch.firstAttempt(period.toString(), factoryId, key, 0,
                "x", false, "HR API down", now.minusSeconds(1800));
        ReflectionTestUtils.setField(failed, "id", UUID.randomUUID());
        when(batches.findByIdempotencyKey(key)).thenReturn(Optional.of(failed));
        when(batches.findById(any())).thenReturn(Optional.of(failed));
        when(payroll.send(any())).thenReturn(new PayrollAck(true, "HR-1", "ok"));

        HrExportBatch batch = service.export(period, factoryId, "scheduler");

        assertThat(batch.getStatus()).isEqualTo("SENT");
        assertThat(batch.getAttempts()).isEqualTo(2);
        assertThat(batch.getLastError()).isNull();
        assertThat(batch.getSentAt()).isEqualTo(now);
        verify(payroll, times(1)).send(any(PayrollBatch.class));
    }

    @Test
    void sentBatchIsReturnedWithoutResending() {
        HrExportBatch sent = HrExportBatch.firstAttempt(period.toString(), factoryId, key, 0,
                "x", true, null, now.minusSeconds(1800));
        when(batches.findByIdempotencyKey(key)).thenReturn(Optional.of(sent));

        assertThat(service.export(period, factoryId, "hr")).isSameAs(sent);
        verify(payroll, never()).send(any());
    }
}
