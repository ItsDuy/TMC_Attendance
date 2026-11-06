package com.tmc.attendance.core;

import com.tmc.attendance.core.ManualEntryService.ManualEntryCommand;
import com.tmc.attendance.core.ManualEntryService.Reason;
import com.tmc.gate.api.GateStatusLookup;
import com.tmc.gate.api.GateStatusLookup.GateStatusView;
import com.tmc.gate.domain.GateDirection;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.domain.CredentialStatus;
import com.tmc.identity.domain.EmployeeStatus;
import com.tmc.security.api.AuditPort;
import com.tmc.shared.error.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The guard's manual entry is a human fallback, not a back door. */
class ManualEntryServiceTest {

    private final UUID binhDuong = UUID.randomUUID();
    private final UUID longAn = UUID.randomUUID();
    private final UUID inGate = UUID.randomUUID();
    private final UUID turnstileGate = UUID.randomUUID();
    private final EmployeeView an = new EmployeeView(UUID.randomUUID(), "BD001", "Nguyen Van An", binhDuong,
            "Assembly", EmployeeStatus.ACTIVE, 1, CredentialStatus.ACTIVE);
    private final EmployeeView minh = new EmployeeView(UUID.randomUUID(), "LA001", "Dang Van Minh", longAn,
            "Assembly", EmployeeStatus.ACTIVE, 1, CredentialStatus.ACTIVE);

    private AttendanceRecorder recorder;
    private AuditPort audit;
    private ManualEntryService service;

    @BeforeEach
    void setUp() {
        EmployeeLookup employees = mock(EmployeeLookup.class);
        when(employees.findByCode("BD001")).thenReturn(Optional.of(an));
        when(employees.findByCode("LA001")).thenReturn(Optional.of(minh));
        GateStatusLookup gates = mock(GateStatusLookup.class);
        when(gates.gateStatuses()).thenReturn(List.of(
                new GateStatusView(inGate, binhDuong, "Gate A - In", GateDirection.IN, null),
                new GateStatusView(turnstileGate, binhDuong, "Gate B - Turnstile", GateDirection.BIDIRECTIONAL, null)));
        AttendanceHistory history = mock(AttendanceHistory.class);
        when(history.lastSettled(any())).thenReturn(Optional.empty());
        recorder = mock(AttendanceRecorder.class);
        audit = mock(AuditPort.class);

        service = new ManualEntryService(employees, gates, history,
                new AccessPolicyChain(List.of(new EmployeeAccessPolicy())), recorder, new ScanResultAssembler(), audit,
                Clock.fixed(Instant.parse("2026-11-07T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void aReasonIsRequired() {
        assertThatThrownBy(() -> service.enter(new ManualEntryCommand("BD001", inGate, null, null, null), "guard"))
                .isInstanceOf(DomainException.class).hasMessageContaining("reason");
    }

    @Test
    void reasonOtherNeedsAnExplanation() {
        assertThatThrownBy(() -> service.enter(new ManualEntryCommand("BD001", inGate, null, Reason.OTHER, " "), "guard"))
                .isInstanceOf(DomainException.class).hasMessageContaining("Explain");
    }

    @Test
    void aGuardCannotLetInSomeoneFromAnotherFactory() {
        ScanResult result = service.enter(
                new ManualEntryCommand("LA001", inGate, null, Reason.PHONE_UNAVAILABLE, null), "guard");

        assertThat(result.outcome()).isEqualTo(ScanOutcome.DENIED);
        verify(recorder, never()).persistManual(any(), any(), any(), any(), anyString(), any(), anyString());
        verify(audit).record(eq("guard"), eq("MANUAL_ENTRY_DENIED"), anyString(), anyString(), any());
    }

    @Test
    void aTwoWayGateNeedsADirection() {
        assertThatThrownBy(() -> service.enter(
                new ManualEntryCommand("BD001", turnstileGate, null, Reason.KIOSK_DOWN, null), "guard"))
                .isInstanceOf(DomainException.class).hasMessageContaining("IN or OUT");
    }

    @Test
    void checkShowsWhoItIsSoTheGuardCanCompareTheIdCard() {
        ManualEntryService.EmployeeCheck check = service.check("BD001");

        assertThat(check.fullName()).isEqualTo("Nguyen Van An");
        assertThat(check.inside()).isFalse();
    }
}
