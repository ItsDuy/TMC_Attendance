package com.tmc.identity.api;

import com.tmc.identity.domain.CredentialStatus;
import com.tmc.identity.domain.Employee;
import com.tmc.identity.domain.EmployeeStatus;
import com.tmc.identity.domain.QrCredential;
import com.tmc.identity.repo.EmployeeRepository;
import com.tmc.identity.repo.FactoryRepository;
import com.tmc.identity.repo.QrCredentialRepository;
import com.tmc.security.api.AuditPort;
import com.tmc.shared.crypto.AesGcmCipher;
import com.tmc.shared.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmployeeService implements EmployeeLookup {

    private final EmployeeRepository employees;
    private final QrCredentialRepository credentials;
    private final FactoryRepository factories;
    private final AesGcmCipher cipher;
    private final AuditPort audit;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employees,
                           QrCredentialRepository credentials,
                           FactoryRepository factories,
                           AesGcmCipher cipher,
                           AuditPort audit,
                           Clock clock) {
        this.employees = employees;
        this.credentials = credentials;
        this.factories = factories;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request, String actor) {
        employees.findByEmployeeCode(request.employeeCode()).ifPresent(existing -> {
            throw DomainException.conflict("EMPLOYEE_CODE_TAKEN",
                    "Employee code already exists: " + request.employeeCode());
        });
        factories.findById(request.factoryId())
                .orElseThrow(() -> DomainException.notFound("Factory", request.factoryId()));

        Employee employee = new Employee(
                request.employeeCode(),
                request.fullName(),
                request.factoryId(),
                request.department(),
                request.email(),
                cipher.encrypt(request.phone()));
        employees.save(employee);

        QrCredential credential = new QrCredential(employee.getId());
        credentials.save(credential);

        audit.record(actor, "EMPLOYEE_CREATED", "Employee", employee.getId().toString(),
                Map.of("employeeCode", employee.getEmployeeCode(), "factoryId", request.factoryId()));

        return toResponse(employee, credential);
    }

    @Transactional
    public EmployeeResponse update(UUID id, UpdateEmployeeRequest request, String actor) {
        Employee employee = employees.findById(id)
                .orElseThrow(() -> DomainException.notFound("Employee", id));
        QrCredential credential = credentialOrThrow(id);

        String phone = request.phone() == null
                ? employee.getPhoneEnc()
                : cipher.encrypt(request.phone());

        employee.update(request.fullName(), request.factoryId(), request.department(),
                request.email(), phone, request.status());

        audit.record(actor, "EMPLOYEE_UPDATED", "Employee", id.toString(),
                Map.of("status", request.status().name()));

        return toResponse(employee, credential);
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list(UUID factoryId) {
        List<Employee> found = factoryId == null
                ? employees.findAll()
                : employees.findByFactoryIdOrderByEmployeeCodeAsc(factoryId);
        return found.stream()
                .map(employee -> toResponse(employee, credentials.findById(employee.getId()).orElse(null)))
                .toList();
    }

    @Transactional
    public QrCredential rotate(UUID employeeId, String actor) {
        QrCredential credential = credentialOrThrow(employeeId);
        credential.rotate(clock.instant());
        credentials.save(credential);
        audit.record(actor, "QR_ROTATED", "QrCredential", employeeId.toString(),
                Map.of("version", credential.getVersion()));
        return credential;
    }

    @Transactional
    public QrCredential revoke(UUID employeeId, String actor) {
        QrCredential credential = credentialOrThrow(employeeId);
        credential.revoke(clock.instant());
        credentials.save(credential);
        audit.record(actor, "QR_REVOKED", "QrCredential", employeeId.toString(),
                Map.of("version", credential.getVersion()));
        return credential;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeView> find(UUID employeeId) {
        return employees.findById(employeeId)
                .map(employee -> toView(employee, credentials.findById(employeeId).orElse(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeView> findByFactory(UUID factoryId) {
        return employees.findByFactoryIdOrderByEmployeeCodeAsc(factoryId).stream()
                .map(employee -> toView(employee,
                        credentials.findById(employee.getId()).orElse(null)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeView> findAll() {
        return employees.findAll().stream()
                .map(employee -> toView(employee,
                        credentials.findById(employee.getId()).orElse(null)))
                .toList();
    }

    private QrCredential credentialOrThrow(UUID employeeId) {
        return credentials.findById(employeeId)
                .orElseThrow(() -> DomainException.notFound("QR credential", employeeId));
    }

    private static EmployeeView toView(Employee employee, QrCredential credential) {
        return new EmployeeView(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getFactoryId(),
                employee.getDepartment(),
                employee.getStatus(),
                credential == null ? 0 : credential.getVersion(),
                credential == null ? CredentialStatus.REVOKED : credential.getStatus());
    }

    private EmployeeResponse toResponse(Employee employee, QrCredential credential) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                employee.getFactoryId(),
                employee.getDepartment(),
                employee.getEmail(),
                cipher.decrypt(employee.getPhoneEnc()),
                employee.getStatus(),
                credential == null ? 0 : credential.getVersion(),
                credential == null ? CredentialStatus.REVOKED : credential.getStatus());
    }

    public static EmployeeStatus defaultStatus() {
        return EmployeeStatus.ACTIVE;
    }
}
