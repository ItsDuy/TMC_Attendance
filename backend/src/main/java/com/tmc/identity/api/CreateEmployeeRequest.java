package com.tmc.identity.api;

import com.tmc.identity.domain.EmployeeStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateEmployeeRequest(
        @NotBlank String employeeCode,
        @NotBlank String fullName,
        @NotNull UUID factoryId,
        @NotBlank String department,
        @Email @NotBlank String email,
        String phone
) {
    public EmployeeStatus statusOrDefault() {
        return EmployeeStatus.ACTIVE;
    }
}
