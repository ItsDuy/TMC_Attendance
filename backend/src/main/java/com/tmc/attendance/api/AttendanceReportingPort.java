package com.tmc.attendance.api;

import com.tmc.attendance.domain.Direction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Query port used by the reporting module. Reporting never touches the
 * attendance repository directly, which keeps the module boundary real.
 */
public interface AttendanceReportingPort {

    List<DirectionAggregate> aggregate(Instant from, Instant to, UUID factoryId);

    List<EmployeeDays> daysPresent(Instant from, Instant to, UUID factoryId);

    /** Every scan whose scan time falls in [from, to), newest first (live site map). */
    List<SiteEvent> events(Instant from, Instant to);

    /** Scans recorded while the gate failed, still waiting for a guard. */
    List<SiteEvent> awaitingGuard();

    /** Entries a security guard recorded by hand, newest first, for HR review. */
    List<ManualEntry> manualEntries(Instant from, Instant to, UUID factoryId);

    record ManualEntry(UUID employeeId, UUID factoryId, UUID gateId, Direction direction, Instant at,
                       String reasonCode, String note, String enteredBy) {
    }

    record SiteEvent(UUID employeeId, UUID factoryId, UUID gateId, Direction direction, String status,
                     String gateOutcome, Instant scannedAt, Instant recordedAt) {
    }

    record DirectionAggregate(UUID employeeId, Direction direction, long eventCount,
                              Instant firstAt, Instant lastAt) {
    }

    record EmployeeDays(UUID employeeId, long daysPresent) {
    }
}
