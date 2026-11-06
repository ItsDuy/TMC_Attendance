package com.tmc.attendance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.AttendanceStatus;
import com.tmc.attendance.domain.Direction;
import com.tmc.attendance.domain.EventSource;
import com.tmc.attendance.repo.AttendanceRecordRepository;
import com.tmc.gate.api.KioskView;
import com.tmc.identity.api.EmployeeView;
import com.tmc.shared.error.DomainException;
import com.tmc.shared.idempotency.ProcessedEvent;
import com.tmc.shared.idempotency.ProcessedEventRepository;
import com.tmc.shared.outbox.OutboxEvent;
import com.tmc.shared.outbox.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns attendance persistence. Every write here is its own transaction so the
 * record is durable before any hardware is touched (ADR-005) and so the outbox
 * row commits atomically with it (ADR-006).
 */
@Service
public class AttendanceRecorder {

    private final AttendanceRecordRepository records;
    private final ProcessedEventRepository processedEvents;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AttendanceRecorder(AttendanceRecordRepository records,
                              ProcessedEventRepository processedEvents,
                              OutboxRepository outbox,
                              ObjectMapper objectMapper,
                              Clock clock) {
        this.records = records;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AttendanceRecord persistPending(ScanCommand command, EmployeeView employee,
                                           KioskView kiosk, Direction direction) {
        Instant now = clock.instant();
        AttendanceRecord record = new AttendanceRecord(
                employee.id(), kiosk.gateId(), kiosk.id(), kiosk.factoryId(),
                direction, command.scannedAt(), now,
                command.offline() ? EventSource.OFFLINE : EventSource.ONLINE);
        records.save(record);

        processedEvents.save(new ProcessedEvent(
                command.clientEventId(), record.getId(),
                payload(Map.of("recordId", record.getId().toString(),
                        "employeeId", employee.id().toString(),
                        "direction", direction.name())),
                now));

        outbox.save(OutboxEvent.pending(
                "AttendanceRecorded", "AttendanceRecord", record.getId().toString(),
                payload(Map.of(
                        "recordId", record.getId().toString(),
                        "employeeId", employee.id().toString(),
                        "employeeCode", employee.employeeCode(),
                        "factoryId", kiosk.factoryId().toString(),
                        "kioskId", kiosk.id().toString(),
                        "direction", direction.name(),
                        "scannedAt", command.scannedAt().toString(),
                        "source", command.offline() ? "OFFLINE" : "ONLINE")),
                now));

        return record;
    }

    @Transactional
    public AttendanceRecord markCompleted(UUID recordId, String gateOutcome) {
        AttendanceRecord record = require(recordId);
        record.complete(gateOutcome);
        return record;
    }

    @Transactional
    public AttendanceRecord markGateUnavailable(UUID recordId, String reason) {
        AttendanceRecord record = require(recordId);
        record.gateUnavailable(reason);
        return record;
    }

    /**
     * A guard let the employee through by hand after the turnstile failed. The record
     * becomes COMPLETED so it is counted in reports and payroll exports.
     */
    @Transactional
    public AttendanceRecord resolveManually(UUID recordId) {
        AttendanceRecord record = require(recordId);
        if (record.getStatus() != AttendanceStatus.GATE_UNAVAILABLE) {
            throw DomainException.conflict("NOT_IN_RECOVERY_QUEUE",
                    "Only records waiting in the recovery queue can be resolved manually");
        }
        record.complete("MANUAL_OVERRIDE");
        return record;
    }

    @Transactional
    public AttendanceRecord markCompletedOffline(UUID recordId) {
        AttendanceRecord record = require(recordId);
        record.complete("OFFLINE_SYNC");
        return record;
    }

    private AttendanceRecord require(UUID recordId) {
        return records.findById(recordId)
                .orElseThrow(() -> new IllegalStateException("Attendance record vanished: " + recordId));
    }

    private String payload(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(values));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialise outbox payload", e);
        }
    }
}
