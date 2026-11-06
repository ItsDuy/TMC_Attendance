package com.tmc.attendance.core;

import java.time.Instant;
import java.util.UUID;

public record ScanResult(
        ScanOutcome outcome,
        UUID recordId,
        String employeeCode,
        String employeeName,
        String direction,
        Instant scannedAt,
        String recordStatus,
        boolean gateOpened,
        boolean manualFallback,
        String message
) {
}
