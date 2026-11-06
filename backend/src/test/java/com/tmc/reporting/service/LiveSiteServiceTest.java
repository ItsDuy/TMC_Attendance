package com.tmc.reporting.service;

import com.tmc.attendance.api.AttendanceReportingPort;
import com.tmc.attendance.api.AttendanceReportingPort.SiteEvent;
import com.tmc.attendance.domain.Direction;
import com.tmc.gate.api.GateStatusLookup;
import com.tmc.gate.api.GateStatusLookup.GateStatusView;
import com.tmc.gate.domain.GateDirection;
import com.tmc.identity.api.AccessScope;
import com.tmc.identity.api.EmployeeLookup;
import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.api.FactoryLookup;
import com.tmc.identity.api.FactoryView;
import com.tmc.identity.domain.CredentialStatus;
import com.tmc.identity.domain.EmployeeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveSiteServiceTest {

    private final Instant now = Instant.parse("2026-11-07T07:00:00Z");
    private final UUID factoryId = UUID.randomUUID();
    private final UUID inGate = UUID.randomUUID();
    private final UUID outGate = UUID.randomUUID();
    private final EmployeeView an = employee("BD001", "Nguyen Van An");
    private final EmployeeView binh = employee("BD002", "Tran Thi Binh", "Quality");
    private final EmployeeView cuong = employee("BD003", "Le Quang Cuong");

    private AttendanceReportingPort attendance;
    private LiveSiteService service;

    @BeforeEach
    void setUp() {
        attendance = mock(AttendanceReportingPort.class);
        GateStatusLookup gates = mock(GateStatusLookup.class);
        EmployeeLookup employees = mock(EmployeeLookup.class);
        FactoryLookup factories = mock(FactoryLookup.class);

        when(factories.findAll()).thenReturn(List.of(new FactoryView(factoryId, "BD", "Binh Duong Factory")));
        when(employees.findAll()).thenReturn(List.of(an, binh, cuong));
        when(gates.gateStatuses()).thenReturn(List.of(
                new GateStatusView(inGate, factoryId, "Gate A - In", GateDirection.IN, now.minusSeconds(10)),
                new GateStatusView(outGate, factoryId, "Gate A - Out", GateDirection.OUT, now.minusSeconds(600))));

        // Newest first, as the port promises.
        when(attendance.events(any(), any())).thenReturn(List.of(
                event(an, inGate, Direction.IN, "COMPLETED", now.minusSeconds(2)),        // inside, door just opened
                event(cuong, inGate, Direction.IN, "GATE_UNAVAILABLE", now.minusSeconds(30)), // stuck, not inside yet
                event(binh, outGate, Direction.OUT, "COMPLETED", now.minusSeconds(300)),   // went home
                event(binh, inGate, Direction.IN, "COMPLETED", now.minusSeconds(3600))));
        when(attendance.awaitingGuard()).thenReturn(List.of(
                event(cuong, inGate, Direction.IN, "GATE_UNAVAILABLE", now.minusSeconds(30))));

        service = new LiveSiteService(attendance, gates, employees, factories, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void onlyPeopleWhoseLastCompletedScanIsInAreInside() {
        LiveSiteService.Site site = service.snapshot(AccessScope.EVERYONE).sites().get(0);

        assertThat(site.employees()).isEqualTo(3);
        assertThat(site.inside()).isEqualTo(1);
        assertThat(site.people()).extracting(LiveSiteService.Person::employeeCode).containsExactly("BD001");
    }

    @Test
    void gateStateShowsJustOpenedStuckAndOffline() {
        List<LiveSiteService.GateState> gates = service.snapshot(AccessScope.EVERYONE).sites().get(0).gates();
        LiveSiteService.GateState in = gates.stream().filter(g -> g.gateId().equals(inGate)).findFirst().orElseThrow();
        LiveSiteService.GateState out = gates.stream().filter(g -> g.gateId().equals(outGate)).findFirst().orElseThrow();

        assertThat(in.online()).isTrue();
        assertThat(in.justOpened()).isTrue();
        assertThat(in.waitingForGuard()).isEqualTo(1);
        assertThat(out.online()).as("no heartbeat for 10 minutes").isFalse();
        assertThat(out.justOpened()).isFalse();
    }

    @Test
    void headCountsOnlyViewerGetsNoNamesAnywhere() {
        LiveSiteService.LiveSites live = service.snapshot(AccessScope.COUNTS_ONLY);

        assertThat(live.namesHidden()).isTrue();
        assertThat(live.sites().get(0).inside()).isEqualTo(1);
        assertThat(live.sites().get(0).people()).isNull();
        assertThat(live.feed()).allSatisfy(item -> {
            assertThat(item.employeeName()).isNull();
            assertThat(item.employeeCode()).isNull();
        });
    }

    @Test
    void supervisorSeesOnlyTheirTeamButGateStateStaysReal() {
        LiveSiteService.LiveSites live = service.snapshot(AccessScope.team(factoryId, "Quality"));
        LiveSiteService.Site site = live.sites().get(0);

        assertThat(live.scope()).isEqualTo("Your team: Quality");
        assertThat(site.employees()).as("team size").isEqualTo(1);
        assertThat(site.inside()).as("Binh went home; An is not in the team").isZero();
        assertThat(live.feed()).extracting(LiveSiteService.FeedItem::employeeCode).containsOnly("BD002");
        assertThat(site.gates().stream().filter(g -> g.gateId().equals(inGate)).findFirst().orElseThrow().justOpened())
                .as("the door opened for someone outside the team: still a real door event").isTrue();
    }

    private EmployeeView employee(String code, String name) {
        return employee(code, name, "Assembly");
    }

    private EmployeeView employee(String code, String name, String department) {
        return new EmployeeView(UUID.randomUUID(), code, name, factoryId, department,
                EmployeeStatus.ACTIVE, 1, CredentialStatus.ACTIVE);
    }

    private SiteEvent event(EmployeeView who, UUID gate, Direction direction, String status, Instant at) {
        return new SiteEvent(who.id(), factoryId, gate, direction, status, null, at, at);
    }
}
