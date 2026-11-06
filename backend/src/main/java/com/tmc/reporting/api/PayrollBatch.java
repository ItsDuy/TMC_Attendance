package com.tmc.reporting.api;

import java.util.List;
import java.util.UUID;

public record PayrollBatch(
        String period,
        UUID factoryId,
        String idempotencyKey,
        String checksum,
        int recordCount,
        List<PayrollRow> rows
) {
    public record PayrollRow(UUID employeeId, String employeeCode, int daysPresent, int checkIns,
                             int checkOuts) {
    }
}
