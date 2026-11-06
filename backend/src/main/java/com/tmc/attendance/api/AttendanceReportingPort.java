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

    record DirectionAggregate(UUID employeeId, Direction direction, long eventCount,
                              Instant firstAt, Instant lastAt) {
    }

    record EmployeeDays(UUID employeeId, long daysPresent) {
    }
}
