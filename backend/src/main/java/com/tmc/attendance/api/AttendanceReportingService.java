package com.tmc.attendance.api;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.AttendanceStatus;
import com.tmc.attendance.domain.EventSource;
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
    public List<SiteEvent> events(Instant from, Instant to) {
        return records.search(from, to, null, null).stream().map(AttendanceReportingService::toEvent).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SiteEvent> awaitingGuard() {
        return records.findByStatusOrderByScannedAtAsc(AttendanceStatus.GATE_UNAVAILABLE).stream()
                .map(AttendanceReportingService::toEvent).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualEntry> manualEntries(Instant from, Instant to, UUID factoryId) {
        return records.findBySourceAndScannedAtGreaterThanEqualAndScannedAtLessThanOrderByScannedAtDesc(
                        EventSource.MANUAL, from, to).stream()
                .filter(r -> factoryId == null || factoryId.equals(r.getFactoryId()))
                .map(r -> new ManualEntry(r.getEmployeeId(), r.getFactoryId(), r.getGateId(), r.getDirection(),
                        r.getScannedAt(), r.getGateOutcome() == null ? null : r.getGateOutcome().replace("MANUAL_", ""),
                        r.getReason(), r.getEnteredBy()))
                .toList();
    }

    private static SiteEvent toEvent(AttendanceRecord r) {
        return new SiteEvent(r.getEmployeeId(), r.getFactoryId(), r.getGateId(), r.getDirection(),
                r.getStatus().name(), r.getGateOutcome(), r.getScannedAt(), r.getRecordedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDays> daysPresent(Instant from, Instant to, UUID factoryId) {
        return records.countDistinctDays(from, to, AttendanceStatus.COMPLETED, factoryId).stream()
                .map(row -> new EmployeeDays(row.getEmployeeId(), row.getDaysPresent()))
                .toList();
    }
}
