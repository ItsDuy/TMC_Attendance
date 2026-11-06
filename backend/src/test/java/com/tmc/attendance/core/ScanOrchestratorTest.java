package com.tmc.attendance.core;

import com.tmc.attendance.api.GateOutcome;
import com.tmc.attendance.api.TurnstilePort;
import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.Direction;
import com.tmc.attendance.domain.EventSource;
import com.tmc.attendance.repo.AttendanceRecordRepository;
import com.tmc.gate.api.KioskLookup;
import com.tmc.gate.api.KioskView;
import com.tmc.gate.domain.GateDirection;
import com.tmc.gate.domain.KioskStatus;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.api.QrClaims;
import com.tmc.identity.domain.CredentialStatus;
import com.tmc.identity.domain.EmployeeStatus;
import com.tmc.security.api.AuditPort;
import com.tmc.shared.idempotency.ProcessedEvent;
import com.tmc.shared.idempotency.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScanOrchestratorTest {

    private static final String TOKEN = "TMC1.payload.signature";

    private final UUID kioskId = UUID.randomUUID();
    private final UUID gateId = UUID.randomUUID();
    private final UUID factoryId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID clientEventId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-23T09:00:00Z");

    private QrTokenVerifier tokenVerifier;
    private AttendanceHistory history;
    private KioskLookup kiosks;
    private AttendanceRecorder recorder;
    private TurnstilePort turnstile;
    private ProcessedEventRepository processedEvents;
    private AttendanceRecordRepository records;
    private EmployeeLookup employees;
    private AuditPort audit;
    private ScanOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        tokenVerifier = mock(QrTokenVerifier.class);
        history = mock(AttendanceHistory.class);
        kiosks = mock(KioskLookup.class);
        recorder = mock(AttendanceRecorder.class);
        turnstile = mock(TurnstilePort.class);
        processedEvents = mock(ProcessedEventRepository.class);
        records = mock(AttendanceRecordRepository.class);
        employees = mock(EmployeeLookup.class);
        audit = mock(AuditPort.class);

        orchestrator = new ScanOrchestrator(
                tokenVerifier,
                history,
                kiosks,
                new AccessPolicyChain(List.of(new EmployeeAccessPolicy())),
                new DirectionResolver(),
                recorder,
                turnstile,
                processedEvents,
                records,
                employees,
                new ScanResultAssembler(),
                audit);

        when(processedEvents.findById(any())).thenReturn(Optional.empty());
        when(kiosks.find(kioskId)).thenReturn(Optional.of(kiosk()));
        when(tokenVerifier.verify(any())).thenReturn(
                new VerifiedScan(new QrClaims(employeeId, "jti", now.plusSeconds(60), 1), employee()));
        when(history.lastSettled(employeeId)).thenReturn(Optional.empty());
    }

    @Test
    void recordIsPersistedBeforeTheGateIsActuated() {
        AttendanceRecord record = pendingRecord();
        when(recorder.persistPending(any(), any(), any(), any())).thenReturn(record);
        when(turnstile.open(gateId.toString())).thenReturn(GateOutcome.success("STUB_OPEN", 2));
        when(recorder.markCompleted(any(), anyString())).thenAnswer(invocation -> {
            record.complete("STUB_OPEN");
            return record;
        });

        ScanResult result = orchestrator.scan(command(false));

        assertThat(result.outcome()).isEqualTo(ScanOutcome.GRANTED);
        assertThat(result.gateOpened()).isTrue();

        InOrder order = inOrder(recorder, turnstile);
        order.verify(recorder).persistPending(any(), any(), any(), any());
        order.verify(turnstile).open(gateId.toString());
        order.verify(recorder).markCompleted(any(), eq("STUB_OPEN"));
    }

    @Test
    void duplicateClientEventIdNeverReachesTheGate() {
        AttendanceRecord original = pendingRecord();
        original.complete("STUB_OPEN");
        ProcessedEvent processed = new ProcessedEvent(clientEventId, UUID.randomUUID(),
                "{}", now);

        when(processedEvents.findById(clientEventId)).thenReturn(Optional.of(processed));
        when(records.findById(processed.getRecordId())).thenReturn(Optional.of(original));
        when(employees.find(employeeId)).thenReturn(Optional.of(employee()));

        ScanResult result = orchestrator.scan(command(false));

        assertThat(result.outcome()).isEqualTo(ScanOutcome.DUPLICATE);
        verify(recorder, never()).persistPending(any(), any(), any(), any());
        verifyNoInteractions(turnstile);
    }

    @Test
    void gateFailureKeepsTheRecordAndFlagsManualFallback() {
        AttendanceRecord record = pendingRecord();
        when(recorder.persistPending(any(), any(), any(), any())).thenReturn(record);
        when(turnstile.open(gateId.toString())).thenReturn(GateOutcome.failure("timeout", 1500));
        when(recorder.markGateUnavailable(any(), anyString())).thenAnswer(invocation -> {
            record.gateUnavailable("timeout");
            return record;
        });

        ScanResult result = orchestrator.scan(command(false));

        assertThat(result.outcome()).isEqualTo(ScanOutcome.GATE_UNAVAILABLE);
        assertThat(result.manualFallback()).isTrue();
        assertThat(result.recordId()).isEqualTo(record.getId());

        InOrder order = inOrder(recorder, turnstile);
        order.verify(recorder).persistPending(any(), any(), any(), any());
        order.verify(turnstile).open(gateId.toString());
        order.verify(recorder).markGateUnavailable(any(), anyString());
    }

    @Test
    void offlineReplayNeverActuatesTheGate() {
        AttendanceRecord record = pendingRecord();
        when(recorder.persistPending(any(), any(), any(), any())).thenReturn(record);
        when(recorder.markCompletedOffline(any())).thenAnswer(invocation -> {
            record.complete("OFFLINE_SYNC");
            return record;
        });

        ScanResult result = orchestrator.scan(command(true));

        assertThat(result.outcome()).isEqualTo(ScanOutcome.GRANTED);
        assertThat(result.gateOpened()).isFalse();
        verifyNoInteractions(turnstile);
    }

    private ScanCommand command(boolean offline) {
        return new ScanCommand(kioskId, clientEventId, TOKEN, null, now, offline);
    }

    private AttendanceRecord pendingRecord() {
        return new AttendanceRecord(employeeId, gateId, kioskId, factoryId, Direction.IN,
                now, now, EventSource.ONLINE);
    }

    private KioskView kiosk() {
        return new KioskView(kioskId, gateId, factoryId, "Gate A - In", GateDirection.IN,
                KioskStatus.ACTIVE);
    }

    private EmployeeView employee() {
        return new EmployeeView(employeeId, "BD001", "Nguyen Van An", factoryId, "Assembly",
                EmployeeStatus.ACTIVE, 1, CredentialStatus.ACTIVE);
    }
}
