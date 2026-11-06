package com.tmc.attendance.core;

import com.tmc.attendance.api.GateOutcome;
import com.tmc.attendance.api.TurnstilePort;
import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.Direction;
import com.tmc.attendance.repo.AttendanceRecordRepository;
import com.tmc.gate.api.KioskLookup;
import com.tmc.gate.api.KioskView;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.security.api.AuditPort;
import com.tmc.shared.idempotency.ProcessedEvent;
import com.tmc.shared.idempotency.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sequences one scan. Deliberately contains no business rules of its own: token
 * verification, the policy chain, persistence and gate actuation are all
 * collaborators behind interfaces (SRP + DIP).
 *
 * <p>Order is the correctness guarantee: persist first, actuate second.</p>
 */
@Service
public class ScanOrchestrator {

    private final QrTokenVerifier tokenVerifier;
    private final AttendanceHistory history;
    private final KioskLookup kiosks;
    private final AccessPolicyChain accessRules;
    private final DirectionResolver directionResolver;
    private final AttendanceRecorder recorder;
    private final TurnstilePort turnstile;
    private final ProcessedEventRepository processedEvents;
    private final AttendanceRecordRepository records;
    private final EmployeeLookup employees;
    private final ScanResultAssembler assembler;
    private final AuditPort audit;

    public ScanOrchestrator(QrTokenVerifier tokenVerifier,
                            AttendanceHistory history,
                            KioskLookup kiosks,
                            AccessPolicyChain accessRules,
                            DirectionResolver directionResolver,
                            AttendanceRecorder recorder,
                            TurnstilePort turnstile,
                            ProcessedEventRepository processedEvents,
                            AttendanceRecordRepository records,
                            EmployeeLookup employees,
                            ScanResultAssembler assembler,
                            AuditPort audit) {
        this.tokenVerifier = tokenVerifier;
        this.history = history;
        this.kiosks = kiosks;
        this.accessRules = accessRules;
        this.directionResolver = directionResolver;
        this.recorder = recorder;
        this.turnstile = turnstile;
        this.processedEvents = processedEvents;
        this.records = records;
        this.employees = employees;
        this.assembler = assembler;
        this.audit = audit;
    }

    public ScanResult scan(ScanCommand command) {
        Optional<ProcessedEvent> prior = processedEvents.findById(command.clientEventId());
        if (prior.isPresent()) {
            return replay(prior.get());
        }

        KioskView kiosk = kiosks.find(command.kioskId())
                .orElseThrow(() -> new ScanDeniedException("KIOSK_UNKNOWN", ScanOutcome.DENIED,
                        "Unknown kiosk"));

        VerifiedScan verified;
        try {
            verified = tokenVerifier.verify(command);
        } catch (ScanDeniedException e) {
            auditDenied(kiosk, e.code());
            return assembler.denied(e);
        }

        AttendanceRecord last = history.lastSettled(verified.employee().id()).orElse(null);
        Direction direction = directionResolver.resolve(kiosk, command.requestedDirection(), last);
        ScanContext context = new ScanContext(
                AccessPoint.kiosk(kiosk), verified.employee(), direction, command.scannedAt(), last);

        Optional<AccessPolicy.Denial> denial = accessRules.firstDenial(context);
        if (denial.isPresent()) {
            auditDenied(kiosk, denial.get().code());
            return assembler.denied(denial.get(), context);
        }

        AttendanceRecord record;
        try {
            record = recorder.persistPending(command, verified.employee(), kiosk, direction);
        } catch (DataIntegrityViolationException e) {
            Optional<ProcessedEvent> raced = processedEvents.findById(command.clientEventId());
            if (raced.isPresent()) {
                return replay(raced.get());
            }
            throw e;
        }

        if (command.offline()) {
            AttendanceRecord synced = recorder.markCompletedOffline(record.getId());
            auditRecorded(synced, verified.employee(), "OFFLINE");
            return assembler.granted(synced, verified.employee(), true);
        }

        GateOutcome outcome;
        try {
            outcome = turnstile.open(kiosk.gateId().toString());
        } catch (RuntimeException e) {
            outcome = GateOutcome.failure(e.getMessage(), 0);
        }

        if (outcome.opened()) {
            AttendanceRecord completed = recorder.markCompleted(record.getId(), outcome.detail());
            auditRecorded(completed, verified.employee(), "ONLINE");
            return assembler.granted(completed, verified.employee(), false);
        }

        AttendanceRecord stuck = recorder.markGateUnavailable(record.getId(), outcome.detail());
        auditRecorded(stuck, verified.employee(), "GATE_UNAVAILABLE");
        return assembler.gateUnavailable(stuck, verified.employee(), outcome.detail());
    }

    private ScanResult replay(ProcessedEvent event) {
        AttendanceRecord record = records.findById(event.getRecordId()).orElseThrow(
                () -> new IllegalStateException("Processed event points at a missing record"));
        EmployeeView employee = employees.find(record.getEmployeeId()).orElse(null);
        if (employee == null) {
            return new ScanResult(ScanOutcome.DUPLICATE, record.getId(), null, null,
                    record.getDirection().name(), record.getScannedAt(), record.getStatus().name(),
                    false, false, "Event was already processed");
        }
        return assembler.duplicate(record, employee);
    }

    private void auditDenied(KioskView kiosk, String code) {
        audit.record("kiosk:" + kiosk.id(), "SCAN_DENIED", "Kiosk", kiosk.id().toString(),
                Map.of("reason", code));
    }

    private void auditRecorded(AttendanceRecord record, EmployeeView employee, String result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", employee.id().toString());
        payload.put("direction", record.getDirection().name());
        payload.put("result", result);
        payload.put("status", record.getStatus().name());
        audit.record("kiosk:" + record.getKioskId(), "ATTENDANCE_RECORDED", "AttendanceRecord",
                record.getId().toString(), payload);
    }
}
