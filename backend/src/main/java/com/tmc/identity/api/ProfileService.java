package com.tmc.identity.api;

import com.tmc.security.api.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** "Who am I?": the signed-in account plus, for employees, their name, code, team and factory. */
@Service
public class ProfileService {

    private final EmployeeLookup employees;
    private final FactoryLookup factories;

    public ProfileService(EmployeeLookup employees, FactoryLookup factories) {
        this.employees = employees;
        this.factories = factories;
    }

    public Profile of(AuthenticatedUser user) {
        EmployeeView employee = user.employeeId() == null ? null : employees.find(user.employeeId()).orElse(null);
        String factoryName = employee == null ? null : factories.findAll().stream()
                .filter(factory -> factory.id().equals(employee.factoryId()))
                .map(FactoryView::name)
                .findFirst()
                .orElse(null);
        return new Profile(user.username(), user.role().name(), user.employeeId(),
                employee == null ? null : employee.employeeCode(),
                employee == null ? null : employee.fullName(),
                employee == null ? null : employee.department(),
                factoryName);
    }

    public record Profile(String username, String role, UUID employeeId, String employeeCode,
                          String fullName, String department, String factoryName) {
    }
}
