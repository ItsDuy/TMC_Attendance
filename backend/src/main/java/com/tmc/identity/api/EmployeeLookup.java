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

    List<EmployeeView> findByFactory(UUID factoryId);

    List<EmployeeView> findAll();
}
