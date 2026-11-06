package com.tmc.reporting.service;

import com.tmc.attendance.api.AttendanceReportingPort;
import com.tmc.attendance.api.AttendanceReportingPort.SiteEvent;
import com.tmc.gate.api.GateStatusLookup;
import com.tmc.gate.api.GateStatusLookup.GateStatusView;
import com.tmc.identity.api.AccessScope;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.api.FactoryLookup;
import com.tmc.identity.api.FactoryView;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Live site map (a lightweight digital-twin view): who is inside each factory (see {@link Occupancy}),
 * what each gate is doing, and the latest scans. Read-only; built from the attendance, gate and identity ports, so
 * it adds no load or risk to the scan path.
 *
 * <p>Turnstiles have no open/closed sensor. "Just opened" means a completed scan in the last few
 * seconds; "stuck" means scans waiting for a guard; "offline" means no kiosk heartbeat recently.
 */
@Service
public class LiveSiteService {

    /** Same horizon anti-passback uses for an open check-in. */
    static final Duration WINDOW = Duration.ofHours(18);
    static final Duration ONLINE_WINDOW = Duration.ofSeconds(45);
    static final Duration OPEN_FLASH = Duration.ofSeconds(4);
    static final int FEED_SIZE = 12;
    static final int PEOPLE_SHOWN = 40;

    private final AttendanceReportingPort attendance;
    private final GateStatusLookup gates;
    private final EmployeeLookup employees;
    private final FactoryLookup factories;
    private final Clock clock;

    public LiveSiteService(AttendanceReportingPort attendance, GateStatusLookup gates,
                           EmployeeLookup employees, FactoryLookup factories, Clock clock) {
        this.attendance = attendance;
        this.gates = gates;
        this.employees = employees;
        this.factories = factories;
        this.clock = clock;
    }

    /**
     * @param scope what the viewer may see. A supervisor's scope is their team: the map then shows
     *              only their factory, and counts, names and the feed cover only their team.
     *              Without {@code namesVisible} (executive) every name and code is left out.
     */
    public LiveSites snapshot(AccessScope scope) {
        boolean includeNames = scope.namesVisible();
        Instant now = clock.instant();
        Map<UUID, EmployeeView> people = employees.findAll().stream()
                .filter(person -> scope.isEveryone() || scope.allows(person))
                .collect(Collectors.toMap(EmployeeView::id, Function.identity()));
        // Gate state (open / stuck) is about the gate, so it uses everyone's scans;
        // who is inside and the feed only use people in scope.
        List<SiteEvent> allEvents = attendance.events(now.minus(WINDOW), now.plusSeconds(60));
        List<SiteEvent> events = allEvents.stream()
                .filter(event -> people.containsKey(event.employeeId()))
                .toList();
        Map<UUID, GateStatusView> gateById = gates.gateStatuses().stream()
                .collect(Collectors.toMap(GateStatusView::gateId, Function.identity()));

        Occupancy occupancy = Occupancy.from(events);
        Map<UUID, Instant> lastOpened = new HashMap<>();
        for (SiteEvent event : allEvents) {
            // A manual entry means a guard let someone through: the system opened no door.
            if ("COMPLETED".equals(event.status()) && !isManual(event)) {
                lastOpened.merge(event.gateId(), event.recordedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        Map<UUID, Long> waiting = attendance.awaitingGuard().stream()
                .collect(Collectors.groupingBy(SiteEvent::gateId, Collectors.counting()));

        List<Site> sites = factories.findAll().stream()
                .filter(factory -> scope.factoryId() == null || scope.factoryId().equals(factory.id()))
                .sorted(Comparator.comparing(FactoryView::code))
                .map(factory -> site(factory, now, includeNames, people, occupancy, lastOpened,
                        waiting, gateById))
                .toList();

        Map<UUID, FactoryView> factoryById = factories.findAll().stream()
                .collect(Collectors.toMap(FactoryView::id, Function.identity()));
        List<FeedItem> feed = events.stream().limit(FEED_SIZE).map(event -> {
            EmployeeView person = people.get(event.employeeId());
            GateStatusView gate = gateById.get(event.gateId());
            FactoryView factory = factoryById.get(event.factoryId());
            return new FeedItem(event.scannedAt(),
                    factory == null ? null : factory.code(),
                    gate == null ? null : gate.name(),
                    event.direction().name(), event.status(),
                    includeNames && person != null ? person.employeeCode() : null,
                    includeNames && person != null ? person.fullName() : null,
                    isManual(event));
        }).toList();

        String scopeLabel = scope.isEveryone() ? null : "Your team: " + scope.department();
        return new LiveSites(now, !includeNames, scopeLabel, sites, feed);
    }

    private Site site(FactoryView factory, Instant now, boolean includeNames,
                      Map<UUID, EmployeeView> people, Occupancy occupancy,
                      Map<UUID, Instant> lastOpened, Map<UUID, Long> waiting,
                      Map<UUID, GateStatusView> gateById) {
        int headcount = (int) people.values().stream()
                .filter(person -> person.factoryId().equals(factory.id())).count();

        List<Person> inside = occupancy.insideOf(factory.id()).stream()
                .map(event -> {
                    EmployeeView person = people.get(event.employeeId());
                    return new Person(person == null ? "?" : person.employeeCode(),
                            person == null ? "?" : person.fullName(), event.scannedAt());
                })
                .toList();

        List<GateState> gateStates = gateById.values().stream()
                .filter(gate -> gate.factoryId().equals(factory.id()))
                .sorted(Comparator.comparing(GateStatusView::name))
                .map(gate -> {
                    Instant opened = lastOpened.get(gate.gateId());
                    boolean online = gate.lastHeartbeatAt() != null
                            && gate.lastHeartbeatAt().isAfter(now.minus(ONLINE_WINDOW));
                    boolean justOpened = opened != null && opened.isAfter(now.minus(OPEN_FLASH));
                    return new GateState(gate.gateId(), gate.name(), gate.direction().name(), online,
                            gate.lastHeartbeatAt(), opened, justOpened,
                            waiting.getOrDefault(gate.gateId(), 0L).intValue());
                })
                .toList();

        return new Site(factory.id(), factory.code(), factory.name(), headcount, inside.size(),
                includeNames ? inside.stream().limit(PEOPLE_SHOWN).toList() : null,
                includeNames ? Math.max(0, inside.size() - PEOPLE_SHOWN) : 0,
                gateStates);
    }

    /** {@code scope} is null when the viewer sees everyone, else e.g. "Your team: Quality". */
    public record LiveSites(Instant generatedAt, boolean namesHidden, String scope, List<Site> sites,
                            List<FeedItem> feed) {
    }

    /** {@code people} is null when names are hidden; {@code peopleNotShown} counts the overflow. */
    public record Site(UUID factoryId, String code, String name, int employees, int inside,
                       List<Person> people, int peopleNotShown, List<GateState> gates) {
    }

    public record Person(String employeeCode, String fullName, Instant since) {
    }

    public record GateState(UUID gateId, String name, String direction, boolean online,
                            Instant lastHeartbeatAt, Instant lastOpenedAt, boolean justOpened,
                            int waitingForGuard) {
    }

    public record FeedItem(Instant at, String factoryCode, String gateName, String direction,
                           String status, String employeeCode, String employeeName, boolean manual) {
    }

    private static boolean isManual(SiteEvent event) {
        return event.gateOutcome() != null && event.gateOutcome().startsWith("MANUAL_");
    }

}
