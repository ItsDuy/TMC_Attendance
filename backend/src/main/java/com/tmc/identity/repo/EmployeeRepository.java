package com.tmc.identity.repo;

import com.tmc.identity.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByEmployeeCode(String employeeCode);

    List<Employee> findByFactoryIdOrderByEmployeeCodeAsc(UUID factoryId);
}
