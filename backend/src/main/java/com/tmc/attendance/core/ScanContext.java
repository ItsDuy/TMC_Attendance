package com.tmc.attendance.core;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.Direction;
import com.tmc.identity.api.EmployeeView;

import java.time.Instant;

public record ScanContext(
        AccessPoint point,
        EmployeeView employee,
        Direction direction,
        Instant scannedAt,
        AttendanceRecord lastRecord
) {
}
