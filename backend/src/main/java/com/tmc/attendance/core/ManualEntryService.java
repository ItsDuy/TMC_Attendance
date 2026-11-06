package com.tmc.attendance.core;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.Direction;
import com.tmc.gate.api.GateStatusLookup;
import com.tmc.gate.api.GateStatusLookup.GateStatusView;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.security.api.AuditPort;
import com.tmc.shared.error.DomainException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Human fallback: a security guard records an entry by hand when the employee's phone, the kiosk
 * or the network is down. It is NOT a back door: the entry goes through the same access policies
 * as a scan (active employee, right factory, anti-passback, gate direction), needs a reason,
 * is marked MANUAL with the guard's name, and is written to the audit log for HR to review.
 */
@Service
public class ManualEntryService {

    public enum Reason { PHONE_UNAVAILABLE, KIOSK_DOWN, NETWORK_DOWN, OTHER }

    public record ManualEntryCommand(String employeeCode, UUID gateId, Direction direction,
                                     Reason reason, String note) {
    }

    /** What the guard sees before confirming: is this the person on the ID card, are they in or out? */
    public record EmployeeCheck(String employeeCode, String fullName, String department,
                                UUID factoryId, boolean inside, Instant lastScanAt,
                                String lastDirection) {
    }

    private final EmployeeLookup employees;
    private final GateStatusLookup gates;
    private final AttendanceHistory history;
    private final AccessPolicyChain accessRules;
    private final AttendanceRecorder recorder;
    private final ScanResultAssembler assembler;
    private final AuditPort audit;
    private final Clock clock;

    public ManualEntryService(EmployeeLookup employees, GateStatusLookup gates, AttendanceHistory history,
                              AccessPolicyChain accessRules, AttendanceRecorder recorder,
                              ScanResultAssembler assembler, AuditPort audit, Clock clock) {
        this.employees = employees;
        this.gates = gates;
        this.history = history;
        this.accessRules = accessRules;
        this.recorder = recorder;
        this.assembler = assembler;
        this.audit = audit;
        this.clock = clock;
    }

    public EmployeeCheck check(String employeeCode) {
        EmployeeView employee = employees.findByCode(employeeCode)
                .orElseThrow(() -> DomainException.notFound("Employee", employeeCode));
        Optional<AttendanceRecord> last = history.lastSettled(employee.id());
        return new EmployeeCheck(employee.employeeCode(), employee.fullName(), employee.department(),
                employee.factoryId(),
                last.map(r -> r.getDirection() == Direction.IN).orElse(false),
                last.map(AttendanceRecord::getScannedAt).orElse(null),
                last.map(r -> r.getDirection().name()).orElse(null));
    }

    public ScanResult enter(ManualEntryCommand command, String guard) {
        if (command.reason() == null) {
            throw DomainException.badRequest("REASON_REQUIRED", "A reason is required for a manual entry");
        }
        String note = command.note() == null ? "" : command.note().trim();
        if (command.reason() == Reason.OTHER && note.isEmpty()) {
            throw DomainException.badRequest("NOTE_REQUIRED", "Explain the reason when choosing OTHER");
        }
        if (note.length() > 160) {
            throw DomainException.badRequest("NOTE_TOO_LONG", "Keep the note under 160 characters");
        }

        EmployeeView employee = employees.findByCode(command.employeeCode())
                .orElseThrow(() -> DomainException.notFound("Employee", command.employeeCode()));
        GateStatusView gate = gates.gateStatuses().stream()
                .filter(g -> g.gateId().equals(command.gateId()))
                .findFirst()
                .orElseThrow(() -> DomainException.notFound("Gate", command.gateId()));

        Direction direction = switch (gate.direction()) {
            case IN -> Direction.IN;
            case OUT -> Direction.OUT;
            case BIDIRECTIONAL -> Optional.ofNullable(command.direction()).orElseThrow(() ->
                    DomainException.badRequest("DIRECTION_REQUIRED", "Choose IN or OUT for this gate"));
        };

        // Same rules as a kiosk scan: the access point is the guard standing at that gate.
        AccessPoint atGate = AccessPoint.guard(gate.gateId(), gate.factoryId(), gate.direction(), guard);
        AttendanceRecord last = history.lastSettled(employee.id()).orElse(null);
        ScanContext context = new ScanContext(atGate, employee, direction, clock.instant(), last);
        Optional<AccessPolicy.Denial> denial = accessRules.firstDenial(context);
        if (denial.isPresent()) {
            audit.record(guard, "MANUAL_ENTRY_DENIED", "Gate", gate.gateId().toString(),
                    Map.of("employeeCode", employee.employeeCode(), "reason", denial.get().code()));
            return assembler.denied(denial.get(), context);
        }

        AttendanceRecord record = recorder.persistManual(employee, gate.gateId(), gate.factoryId(), direction,
                command.reason().name(), note.isEmpty() ? null : note, guard);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordId", record.getId().toString());
        payload.put("employeeCode", employee.employeeCode());
        payload.put("direction", direction.name());
        payload.put("reason", command.reason().name());
        if (!note.isEmpty()) {
            payload.put("note", note);
        }
        audit.record(guard, "MANUAL_ENTRY", "AttendanceRecord", record.getId().toString(), payload);
        return assembler.manual(record, employee);
    }
}
