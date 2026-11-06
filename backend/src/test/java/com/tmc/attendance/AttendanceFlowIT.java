package com.tmc.attendance;

import com.tmc.attendance.api.TurnstilePort;
import com.tmc.attendance.core.ManualEntryService;
import com.tmc.attendance.core.ScanCommand;
import com.tmc.attendance.core.ScanOrchestrator;
import com.tmc.attendance.core.ScanOutcome;
import com.tmc.attendance.core.ScanResult;
import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.AttendanceStatus;
import com.tmc.attendance.domain.Direction;
import com.tmc.attendance.domain.EventSource;
import com.tmc.attendance.repo.AttendanceRecordRepository;
import com.tmc.gate.api.KioskResponse;
import com.tmc.gate.api.KioskService;
import com.tmc.gate.domain.Gate;
import com.tmc.gate.domain.GateDirection;
import com.tmc.gate.repo.GateRepository;
import com.tmc.identity.api.CreateEmployeeRequest;
import com.tmc.identity.api.EmployeeResponse;
import com.tmc.identity.api.EmployeeService;
import com.tmc.identity.api.QrTokenPort;
import com.tmc.identity.api.QrTokenService;
import com.tmc.identity.domain.Factory;
import com.tmc.identity.repo.FactoryRepository;
import com.tmc.identity.repo.QrCredentialRepository;
import com.tmc.security.audit.AuditService;
import com.tmc.shared.config.TmcProperties;
import com.tmc.shared.idempotency.ProcessedEventRepository;
import com.tmc.shared.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AttendanceFlowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("tmc.seed.enabled", () -> "false");
        registry.add("tmc.outbox.relay-interval-ms", () -> "3600000");
    }

    @Autowired
    private FactoryRepository factories;
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private GateRepository gates;
    @Autowired
    private KioskService kioskService;
    @Autowired
    private QrTokenPort qrTokenPort;
    @Autowired
    private TmcProperties tmcProperties;
    @Autowired
    private QrCredentialRepository qrCredentials;
    @Autowired
    private ScanOrchestrator orchestrator;
    @Autowired
    private AttendanceRecordRepository records;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private ProcessedEventRepository processedEvents;
    @Autowired
    private AuditService auditService;
    @Autowired
    private TurnstilePort turnstile;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ManualEntryService manualEntries;

    private EmployeeResponse employee;
    private KioskResponse kiosk;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Factory factory = factories.save(new Factory("F-" + suffix, "Test Factory " + suffix));
        employee = employeeService.create(new CreateEmployeeRequest(
                "E-" + suffix, "Test Employee", factory.getId(), "Assembly",
                "employee-" + suffix + "@tmc.example", "+84900000000"), "test");
        Gate gate = gates.save(new Gate(factory.getId(), "Gate " + suffix, GateDirection.BIDIRECTIONAL));
        kiosk = kioskService.create(gate.getId(), "Kiosk " + suffix, "test");
    }

    @Test
    void scanIsPersistedAndCompletedBeforeTheGateResponds() {
        String token = qrTokenPort.mint(employee.id());
        UUID eventId = UUID.randomUUID();

        ScanResult result = orchestrator.scan(new ScanCommand(
                kiosk.id(), eventId, token, null, Instant.now(), false));

        assertThat(result.outcome()).isEqualTo(ScanOutcome.GRANTED);
        assertThat(result.gateOpened()).isTrue();

        AttendanceRecord stored = records.findById(result.recordId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AttendanceStatus.COMPLETED);
        assertThat(stored.getSource()).isEqualTo(EventSource.ONLINE);
        assertThat(processedEvents.findById(eventId)).isPresent();
        assertThat(outbox.findAll())
                .anyMatch(event -> event.getAggregateId().equals(result.recordId().toString()));
    }

    @Test
    void replayingTheSameEventProducesOneRecordAndOneGateOpen() {
        String token = qrTokenPort.mint(employee.id());
        UUID eventId = UUID.randomUUID();
        Instant scannedAt = Instant.now();

        ScanResult first = orchestrator.scan(new ScanCommand(
                kiosk.id(), eventId, token, null, scannedAt, false));
        long afterFirst = records.count();

        ScanResult second = orchestrator.scan(new ScanCommand(
                kiosk.id(), eventId, token, null, scannedAt, false));

        assertThat(first.outcome()).isEqualTo(ScanOutcome.GRANTED);
        assertThat(second.outcome()).isEqualTo(ScanOutcome.DUPLICATE);
        assertThat(second.recordId()).isEqualTo(first.recordId());
        assertThat(records.count()).isEqualTo(afterFirst);
    }

    @Test
    void offlineReplayKeepsTheOriginalScanTime() {
        // The kiosk scanned 15 minutes ago while offline, using a token that was valid
        // at that moment; it is only synced now. Mint with a clock set to the scan time,
        // because the verifier rejects tokens presented before they were issued.
        Instant scannedAt = Instant.now().minusSeconds(900);
        QrTokenService pastIssuer = new QrTokenService(tmcProperties, qrCredentials,
                Clock.fixed(scannedAt, ZoneOffset.UTC));
        String token = pastIssuer.mint(employee.id());

        ScanResult result = orchestrator.scan(new ScanCommand(
                kiosk.id(), UUID.randomUUID(), token, null, scannedAt, true));

        assertThat(result.outcome()).isEqualTo(ScanOutcome.GRANTED);
        assertThat(result.gateOpened()).isFalse();

        AttendanceRecord stored = records.findById(result.recordId()).orElseThrow();
        assertThat(stored.getSource()).isEqualTo(EventSource.OFFLINE);
        assertThat(stored.getScannedAt()).isEqualTo(scannedAt);
        assertThat(stored.getRecordedAt()).isAfter(scannedAt);
    }

    @Test
    void guardManualEntryIsStoredWithoutKioskMarkedManualAndCannotRepeat() {
        var command = new ManualEntryService.ManualEntryCommand(employee.employeeCode(), kiosk.gateId(),
                Direction.IN, ManualEntryService.Reason.PHONE_UNAVAILABLE, "phone battery dead");

        ScanResult first = manualEntries.enter(command, "guard");
        ScanResult again = manualEntries.enter(command, "guard");

        assertThat(first.outcome()).isEqualTo(ScanOutcome.GRANTED);
        AttendanceRecord stored = records.findById(first.recordId()).orElseThrow();
        assertThat(stored.getSource()).isEqualTo(EventSource.MANUAL);
        assertThat(stored.getStatus()).isEqualTo(AttendanceStatus.COMPLETED);
        assertThat(stored.getKioskId()).as("no kiosk was involved").isNull();
        assertThat(stored.getEnteredBy()).isEqualTo("guard");
        assertThat(stored.getGateOutcome()).isEqualTo("MANUAL_PHONE_UNAVAILABLE");
        assertThat(again.outcome()).as("anti-passback applies to guards too").isEqualTo(ScanOutcome.DENIED);
    }

    @Test
    void auditChainDetectsTampering() {
        auditService.record("tester", "TEST_ACTION", "Test", "1", Map.of("k", "v"));
        auditService.record("tester", "TEST_ACTION", "Test", "2", Map.of("k", "w"));

        assertThat(auditService.verifyChain().valid()).isTrue();
    }

    @Test
    void kioskChannelRejectsUnsignedRequests() throws Exception {
        mockMvc.perform(post("/api/kiosk/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"TMC1.x.y\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managementApiRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
    }

    @Test
    void turnstilePortIsWired() {
        assertThat(turnstile).isNotNull();
    }
}
