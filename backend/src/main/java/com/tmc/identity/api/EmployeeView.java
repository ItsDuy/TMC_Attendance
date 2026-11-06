package com.tmc.identity.api;

import com.tmc.identity.domain.CredentialStatus;
import com.tmc.identity.domain.EmployeeStatus;

import java.util.UUID;

/**
 * Read model of an employee as seen by other modules. Deliberately excludes PII
 * that consumers do not need (privacy-by-design, ADR-019).
 */
public record EmployeeView(
        UUID id,
        String employeeCode,
        String fullName,
        UUID factoryId,
        String department,
        EmployeeStatus status,
        int credentialVersion,
        CredentialStatus credentialStatus
) {
}
