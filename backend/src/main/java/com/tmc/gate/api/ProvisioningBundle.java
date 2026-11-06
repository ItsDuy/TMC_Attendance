package com.tmc.gate.api;

import java.util.List;
import java.util.UUID;

/**
 * Everything a kiosk needs to validate QR codes while offline (ADR-016).
 */
public record ProvisioningBundle(
        String tokenFormat,
        long ttlSeconds,
        UUID kioskId,
        UUID factoryId,
        UUID gateId,
        String gateDirection,
        int configVersion,
        List<EmployeeKey> employees
) {
    public record EmployeeKey(UUID employeeId, String employeeCode, String fullName, int version,
                              String key) {
    }
}
