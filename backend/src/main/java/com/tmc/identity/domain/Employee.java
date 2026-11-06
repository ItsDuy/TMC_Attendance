package com.tmc.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "employee_code", nullable = false, unique = true)
    private String employeeCode;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "factory_id", nullable = false)
    private UUID factoryId;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "phone_enc")
    private String phoneEnc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Employee() {
    }

    public Employee(String employeeCode, String fullName, UUID factoryId, String department,
                    String email, String phoneEnc) {
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.factoryId = factoryId;
        this.department = department;
        this.email = email;
        this.phoneEnc = phoneEnc;
        this.status = EmployeeStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String fullName, UUID factoryId, String department, String email,
                       String phoneEnc, EmployeeStatus status) {
        this.fullName = fullName;
        this.factoryId = factoryId;
        this.department = department;
        this.email = email;
        this.phoneEnc = phoneEnc;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public UUID getFactoryId() {
        return factoryId;
    }

    public String getDepartment() {
        return department;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneEnc() {
        return phoneEnc;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
