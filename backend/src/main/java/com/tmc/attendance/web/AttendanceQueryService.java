package com.tmc.attendance.web;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.AttendanceStatus;
import com.tmc.attendance.repo.AttendanceRecordRepository;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AttendanceQueryService {

    private final AttendanceRecordRepository records;
    private final EmployeeLookup employees;

    public AttendanceQueryService(AttendanceRecordRepository records, EmployeeLookup employees) {
        this.records = records;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordView> search(Instant from, Instant to, UUID employeeId, UUID factoryId) {
        Map<UUID, EmployeeView> directory = directory(employeeId, factoryId);
        return records.search(from, to, employeeId, factoryId).stream()
                .map(record -> AttendanceRecordView.from(record, directory.get(record.getEmployeeId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordView> byStatus(AttendanceStatus status) {
        Map<UUID, EmployeeView> directory = directory(null, null);
        return records.findByStatusOrderByScannedAtAsc(status).stream()
                .map(record -> AttendanceRecordView.from(record, directory.get(record.getEmployeeId())))
                .toList();
    }

    private Map<UUID, EmployeeView> directory(UUID employeeId, UUID factoryId) {
        List<EmployeeView> people = employeeId != null
                ? employees.find(employeeId).map(List::of).orElse(List.of())
                : factoryId != null ? employees.findByFactory(factoryId) : employees.findAll();
        return people.stream().collect(Collectors.toMap(EmployeeView::id, Function.identity()));
    }

    public record AttendanceRecordView(
            UUID id,
            UUID employeeId,
            String employeeCode,
            String employeeName,
            UUID factoryId,
            UUID gateId,
            UUID kioskId,
            String direction,
            Instant scannedAt,
            Instant recordedAt,
            String status,
            String gateOutcome,
            String source,
            String reason
    ) {
        static AttendanceRecordView from(AttendanceRecord record, EmployeeView employee) {
            return new AttendanceRecordView(
                    record.getId(),
                    record.getEmployeeId(),
                    employee == null ? null : employee.employeeCode(),
                    employee == null ? null : employee.fullName(),
                    record.getFactoryId(),
                    record.getGateId(),
                    record.getKioskId(),
                    record.getDirection().name(),
                    record.getScannedAt(),
                    record.getRecordedAt(),
                    record.getStatus().name(),
                    record.getGateOutcome(),
                    record.getSource().name(),
                    record.getReason());
        }
    }
}
