package com.tmc.reporting.service;

import com.tmc.attendance.api.AttendanceReportingPort;
import com.tmc.attendance.api.AttendanceReportingPort.SiteEvent;
import com.tmc.gate.api.GateStatusLookup;
import com.tmc.gate.api.GateStatusLookup.GateStatusView;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.api.FactoryLookup;
import com.tmc.identity.api.FactoryView;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Evacuation (mustering) list for a fire drill: everyone inside, per factory, sorted by name, no cap.
 * Same "who is inside" rule as the live map ({@link Occupancy}). Depends only on other modules'
 * ports, never on their repositories (Dependency Inversion).
 */
@Service
public class EvacuationService {

    private final AttendanceReportingPort attendance;
    private final GateStatusLookup gates;
    private final EmployeeLookup employees;
    private final FactoryLookup factories;
    private final Clock clock;

    public EvacuationService(AttendanceReportingPort attendance, GateStatusLookup gates,
                             EmployeeLookup employees, FactoryLookup factories, Clock clock) {
        this.attendance = attendance;
        this.gates = gates;
        this.employees = employees;
        this.factories = factories;
        this.clock = clock;
    }

    public Evacuation evacuation() {
        Instant now = clock.instant();
        Map<UUID, EmployeeView> people = employees.findAll().stream()
                .collect(Collectors.toMap(EmployeeView::id, Function.identity()));
        Map<UUID, String> gateNames = gates.gateStatuses().stream()
                .collect(Collectors.toMap(GateStatusView::gateId, GateStatusView::name));
        Occupancy occupancy = Occupancy.from(
                attendance.events(now.minus(LiveSiteService.WINDOW), now.plusSeconds(60)));

        List<EvacuationSite> sites = factories.findAll().stream()
                .sorted(Comparator.comparing(FactoryView::code))
                .map(factory -> {
                    List<EvacuationPerson> inside = occupancy.insideOf(factory.id()).stream()
                            .map(event -> person(event, people.get(event.employeeId()), gateNames))
                            .sorted(Comparator.comparing(EvacuationPerson::fullName))
                            .toList();
                    return new EvacuationSite(factory.code(), factory.name(), inside.size(), inside);
                })
                .toList();
        return new Evacuation(now, sites);
    }

    private static EvacuationPerson person(SiteEvent event, EmployeeView p, Map<UUID, String> gateNames) {
        return new EvacuationPerson(p == null ? "?" : p.employeeCode(), p == null ? "?" : p.fullName(),
                p == null ? null : p.department(), event.scannedAt(), gateNames.get(event.gateId()));
    }

    public record Evacuation(Instant generatedAt, List<EvacuationSite> sites) {
    }

    public record EvacuationSite(String code, String name, int inside, List<EvacuationPerson> people) {
    }

    public record EvacuationPerson(String employeeCode, String fullName, String department, Instant inSince,
                                   String enteredThrough) {
    }
}
