package com.tmc.attendance.core;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.AttendanceStatus;
import com.tmc.attendance.repo.AttendanceRecordRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model over attendance history used by the policies. Keeps JPA out of the
 * policy classes.
 */
@Component
public class AttendanceHistory {

    private static final List<AttendanceStatus> SETTLED =
            List.of(AttendanceStatus.COMPLETED, AttendanceStatus.GATE_UNAVAILABLE);

    private final AttendanceRecordRepository records;

    public AttendanceHistory(AttendanceRecordRepository records) {
        this.records = records;
    }

    public Optional<AttendanceRecord> lastSettled(UUID employeeId) {
        return records.findTopByEmployeeIdAndStatusInOrderByScannedAtDesc(employeeId, SETTLED);
    }
}
