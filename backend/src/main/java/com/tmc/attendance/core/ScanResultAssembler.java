package com.tmc.attendance.core;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.identity.api.EmployeeView;
import org.springframework.stereotype.Component;

@Component
public class ScanResultAssembler {

    public ScanResult granted(AttendanceRecord record, EmployeeView employee, boolean offline) {
        return new ScanResult(ScanOutcome.GRANTED, record.getId(), employee.employeeCode(),
                employee.fullName(), record.getDirection().name(), record.getScannedAt(),
                record.getStatus().name(), !offline, false,
                offline ? "Recorded from offline buffer; gate was not actuated" : "Access granted");
    }

    public ScanResult gateUnavailable(AttendanceRecord record, EmployeeView employee, String reason) {
        return new ScanResult(ScanOutcome.GATE_UNAVAILABLE, record.getId(), employee.employeeCode(),
                employee.fullName(), record.getDirection().name(), record.getScannedAt(),
                record.getStatus().name(), false, true,
                "Attendance recorded but the gate did not open: " + reason);
    }

    public ScanResult duplicate(AttendanceRecord record, EmployeeView employee) {
        return new ScanResult(ScanOutcome.DUPLICATE, record.getId(), employee.employeeCode(),
                employee.fullName(), record.getDirection().name(), record.getScannedAt(),
                record.getStatus().name(), false, record.getStatus().name().equals("GATE_UNAVAILABLE"),
                "Event was already processed; returning the original result");
    }

    public ScanResult denied(ScanDeniedException exception) {
        return new ScanResult(exception.outcome(), null, null, null, null, null, null,
                false, false, exception.getMessage());
    }

    public ScanResult denied(AccessPolicy.Denial denial) {
        return new ScanResult(ScanOutcome.DENIED, null, null, null, null, null, null,
                false, false, denial.message());
    }
}
