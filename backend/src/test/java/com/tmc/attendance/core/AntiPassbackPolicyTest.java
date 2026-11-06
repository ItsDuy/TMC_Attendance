package com.tmc.attendance.core;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.Direction;
import com.tmc.attendance.domain.EventSource;
import com.tmc.gate.api.KioskView;
import com.tmc.gate.domain.GateDirection;
import com.tmc.gate.domain.KioskStatus;
import com.tmc.identity.api.EmployeeView;
import com.tmc.identity.domain.CredentialStatus;
import com.tmc.identity.domain.EmployeeStatus;
import com.tmc.shared.config.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AntiPassbackPolicyTest {

    private final UUID factoryId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID kioskId = UUID.randomUUID();
    private final UUID gateId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-23T09:00:00Z");

    private AntiPassbackPolicy policy;

    @BeforeEach
    void setUp() {
        SystemConfigService config = mock(SystemConfigService.class);
        when(config.getInt(org.mockito.ArgumentMatchers.anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        policy = new AntiPassbackPolicy(config);
    }

    @Test
    void firstCheckInIsAllowed() {
        assertThat(policy.evaluate(context(Direction.IN, null))).isEmpty();
    }

    @Test
    void checkOutWithoutCheckInIsDenied() {
        assertThat(policy.evaluate(context(Direction.OUT, null)))
                .hasValueSatisfying(denial -> assertThat(denial.code()).isEqualTo("NO_CHECK_IN"));
    }

    @Test
    void repeatedCheckInInsideCooldownIsDenied() {
        AttendanceRecord last = record(Direction.IN, now.minusSeconds(60));

        assertThat(policy.evaluate(context(Direction.IN, last)))
                .hasValueSatisfying(denial -> assertThat(denial.code()).isEqualTo("DUPLICATE_SCAN"));
    }

    @Test
    void repeatedCheckInAfterCooldownIsAllowed() {
        AttendanceRecord last = record(Direction.IN, now.minusSeconds(600));

        assertThat(policy.evaluate(context(Direction.IN, last))).isEmpty();
    }

    @Test
    void checkOutAfterCheckInIsAllowed() {
        AttendanceRecord last = record(Direction.IN, now.minusSeconds(3600));

        assertThat(policy.evaluate(context(Direction.OUT, last))).isEmpty();
    }

    @Test
    void checkOutWithoutAFreshCheckInIsDenied() {
        AttendanceRecord last = record(Direction.IN, now.minusSeconds(20 * 3600));

        assertThat(policy.evaluate(context(Direction.OUT, last)))
                .hasValueSatisfying(denial -> assertThat(denial.code()).isEqualTo("CHECK_IN_TOO_OLD"));
    }

    @Test
    void twoCheckOutsInARowAreDenied() {
        AttendanceRecord last = record(Direction.OUT, now.minusSeconds(3600));

        assertThat(policy.evaluate(context(Direction.OUT, last)))
                .hasValueSatisfying(denial -> assertThat(denial.code()).isEqualTo("NO_CHECK_IN"));
    }

    private ScanContext context(Direction direction, AttendanceRecord last) {
        return new ScanContext(AccessPoint.kiosk(kiosk()), employee(), direction, now, last);
    }

    private AttendanceRecord record(Direction direction, Instant scannedAt) {
        AttendanceRecord record = new AttendanceRecord(employeeId, gateId, kioskId, factoryId,
                direction, scannedAt, scannedAt, EventSource.ONLINE);
        record.complete("STUB_OPEN");
        return record;
    }

    private KioskView kiosk() {
        return new KioskView(kioskId, gateId, factoryId, "Gate A", GateDirection.BIDIRECTIONAL,
                KioskStatus.ACTIVE);
    }

    private EmployeeView employee() {
        return new EmployeeView(employeeId, "BD001", "Nguyen Van An", factoryId, "Assembly",
                EmployeeStatus.ACTIVE, 1, CredentialStatus.ACTIVE);
    }
}
