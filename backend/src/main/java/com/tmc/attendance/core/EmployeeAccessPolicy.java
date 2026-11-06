package com.tmc.attendance.core;

import com.tmc.identity.domain.CredentialStatus;
import com.tmc.identity.domain.EmployeeStatus;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The employee must exist, be active, hold a live credential and belong to the
 * factory of the gate (kiosk or guard) where the decision is made.
 */
@Component
@Order(10)
public class EmployeeAccessPolicy implements AccessPolicy {

    @Override
    public Optional<Denial> evaluate(ScanContext context) {
        if (context.employee().status() != EmployeeStatus.ACTIVE) {
            return Optional.of(new Denial("EMPLOYEE_INACTIVE", "Employee is not active"));
        }
        if (context.employee().credentialStatus() != CredentialStatus.ACTIVE) {
            return Optional.of(new Denial("QR_REVOKED", "QR credential has been revoked"));
        }
        if (!context.employee().factoryId().equals(context.point().factoryId())) {
            return Optional.of(new Denial("WRONG_FACTORY", "Employee belongs to another factory"));
        }
        return Optional.empty();
    }
}
