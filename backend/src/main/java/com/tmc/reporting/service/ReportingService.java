package com.tmc.reporting.service;

import com.tmc.attendance.api.AttendanceReportingPort;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.reporting.api.MonthlyReportRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportingService {

    private final AttendanceReportingPort attendance;
    private final EmployeeLookup employees;

    public ReportingService(AttendanceReportingPort attendance, EmployeeLookup employees) {
        this.attendance = attendance;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public List<MonthlyReportRow> monthly(YearMonth period, UUID factoryId) {
        Instant from = period.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = period.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<UUID, EmployeeView> directory = new HashMap<>();
        List<EmployeeView> people = factoryId == null
                ? employees.findAll()
                : employees.findByFactory(factoryId);
        people.forEach(person -> directory.put(person.id(), person));

        Map<UUID, MutableRow> rows = new HashMap<>();
        for (AttendanceReportingPort.DirectionAggregate aggregate : attendance
                .aggregate(from, to, factoryId)) {
            rows.computeIfAbsent(aggregate.employeeId(),
                            id -> new MutableRow(directory.get(id), factoryId))
                    .add(aggregate);
        }

        for (AttendanceReportingPort.EmployeeDays days : attendance
                .daysPresent(from, to, factoryId)) {
            MutableRow row = rows.get(days.employeeId());
            if (row != null) {
                row.daysPresent = (int) days.daysPresent();
            }
        }

        List<MonthlyReportRow> result = new ArrayList<>(rows.values().stream()
                .map(MutableRow::toRow)
                .toList());
        result.sort((left, right) -> String.valueOf(left.employeeCode())
                .compareTo(String.valueOf(right.employeeCode())));
        return result;
    }

    private static final class MutableRow {
        private final EmployeeView employee;
        private final UUID factoryId;
        private int daysPresent;
        private int checkIns;
        private int checkOuts;
        private Instant firstCheckIn;
        private Instant lastCheckOut;

        private MutableRow(EmployeeView employee, UUID factoryId) {
            this.employee = employee;
            this.factoryId = factoryId;
        }

        void add(AttendanceReportingPort.DirectionAggregate aggregate) {
            switch (aggregate.direction()) {
                case IN -> {
                    checkIns += (int) aggregate.eventCount();
                    if (firstCheckIn == null || aggregate.firstAt().isBefore(firstCheckIn)) {
                        firstCheckIn = aggregate.firstAt();
                    }
                }
                case OUT -> {
                    checkOuts += (int) aggregate.eventCount();
                    if (lastCheckOut == null || aggregate.lastAt().isAfter(lastCheckOut)) {
                        lastCheckOut = aggregate.lastAt();
                    }
                }
            }
        }

        MonthlyReportRow toRow() {
            return new MonthlyReportRow(
                    employee == null ? null : employee.id(),
                    employee == null ? null : employee.employeeCode(),
                    employee == null ? null : employee.fullName(),
                    employee == null ? factoryId : employee.factoryId(),
                    daysPresent,
                    checkIns,
                    checkOuts,
                    firstCheckIn,
                    lastCheckOut);
        }
    }
}
