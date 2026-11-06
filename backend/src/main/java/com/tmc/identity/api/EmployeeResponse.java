package com.tmc.identity.api;

import com.tmc.identity.domain.CredentialStatus;
import com.tmc.identity.domain.EmployeeStatus;

import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String employeeCode,
        String fullName,
        UUID factoryId,
        String department,
        String email,
        String phone,
        EmployeeStatus status,
        int credentialVersion,
        CredentialStatus credentialStatus
) {
}
