package com.tmc.identity.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Narrow port used by the attendance and reporting modules. Callers never see
 * the persistence model.
 */
public interface EmployeeLookup {

    Optional<EmployeeView> find(UUID employeeId);

    /** By the code printed on the employee's ID card, e.g. BD004. */
    Optional<EmployeeView> findByCode(String employeeCode);

    List<EmployeeView> findByFactory(UUID factoryId);

    List<EmployeeView> findAll();
}
