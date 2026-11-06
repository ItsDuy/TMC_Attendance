package com.tmc.attendance.core;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.Direction;
import com.tmc.gate.api.KioskView;
import com.tmc.identity.api.EmployeeView;

import java.time.Instant;

public record ScanContext(
        KioskView kiosk,
        EmployeeView employee,
        Direction direction,
        Instant scannedAt,
        AttendanceRecord lastRecord
) {
}
