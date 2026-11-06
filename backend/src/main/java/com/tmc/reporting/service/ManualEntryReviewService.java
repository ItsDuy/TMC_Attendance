package com.tmc.reporting.service;

import com.tmc.attendance.api.AttendanceReportingPort;
import com.tmc.gate.api.GateStatusLookup;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.api.FactoryLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HR's review list of entries a security guard recorded by hand: who, where, why and which guard.
 * Guards act, HR reviews, admin audits (separation of duties). Its own class because it changes
 * for a different reason than the monthly report (Single Responsibility).
 */
@Service
public class ManualEntryReviewService {

    private final AttendanceReportingPort attendance;
    private final EmployeeLookup employees;
    private final FactoryLookup factories;
    private final GateStatusLookup gates;

    public ManualEntryReviewService(AttendanceReportingPort attendance, EmployeeLookup employees,
                                    FactoryLookup factories, GateStatusLookup gates) {
        this.attendance = attendance;
        this.employees = employees;
        this.factories = factories;
        this.gates = gates;
    }

    @Transactional(readOnly = true)
    public List<ManualEntryLine> forMonth(YearMonth period) {
        Instant from = period.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = period.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<UUID, EmployeeView> directory = new HashMap<>();
        employees.findAll().forEach(person -> directory.put(person.id(), person));
        Map<UUID, String> gateNames = new HashMap<>();
        gates.gateStatuses().forEach(gate -> gateNames.put(gate.gateId(), gate.name()));
        Map<UUID, String> factoryCodes = new HashMap<>();
        factories.findAll().forEach(factory -> factoryCodes.put(factory.id(), factory.code()));

        return attendance.manualEntries(from, to, null).stream().map(entry -> {
            EmployeeView person = directory.get(entry.employeeId());
            return new ManualEntryLine(entry.at(), factoryCodes.get(entry.factoryId()),
                    gateNames.get(entry.gateId()), entry.direction().name(),
                    person == null ? null : person.employeeCode(), person == null ? null : person.fullName(),
                    entry.reasonCode(), entry.note(), entry.enteredBy());
        }).toList();
    }

    public record ManualEntryLine(Instant at, String factoryCode, String gateName, String direction,
                                  String employeeCode, String employeeName, String reason, String note,
                                  String enteredBy) {
    }
}
