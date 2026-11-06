package com.tmc.reporting.api;

import java.time.Instant;
import java.util.UUID;

public record MonthlyReportRow(
        UUID employeeId,
        String employeeCode,
        String employeeName,
        UUID factoryId,
        int daysPresent,
        int checkIns,
        int checkOuts,
        Instant firstCheckIn,
        Instant lastCheckOut,
        /** Entries a guard recorded by hand this month: HR reviews these. */
        int manualEntries
) {
}
