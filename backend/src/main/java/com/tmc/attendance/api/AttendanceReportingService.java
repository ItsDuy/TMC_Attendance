package com.tmc.attendance.api;

import com.tmc.attendance.domain.AttendanceStatus;
import com.tmc.attendance.repo.AttendanceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceReportingService implements AttendanceReportingPort {

    private final AttendanceRecordRepository records;

    public AttendanceReportingService(AttendanceRecordRepository records) {
        this.records = records;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectionAggregate> aggregate(Instant from, Instant to, UUID factoryId) {
        return records.aggregateByEmployee(from, to, AttendanceStatus.COMPLETED, factoryId).stream()
                .map(row -> new DirectionAggregate(row.getEmployeeId(), row.getDirection(),
                        row.getEventCount(), row.getFirstAt(), row.getLastAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDays> daysPresent(Instant from, Instant to, UUID factoryId) {
        return records.countDistinctDays(from, to, AttendanceStatus.COMPLETED, factoryId).stream()
                .map(row -> new EmployeeDays(row.getEmployeeId(), row.getDaysPresent()))
                .toList();
    }
}
