package com.tmc.attendance.core;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.Direction;
import com.tmc.shared.config.SystemConfigService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Anti-passback (ADR-015). Stops a badge or phone being passed back to a second
 * person and blocks out-of-order IN/OUT sequences.
 */
@Component
@Order(20)
public class AntiPassbackPolicy implements AccessPolicy {

    private static final int DEFAULT_COOLDOWN_SECONDS = 180;
    private static final int DEFAULT_MAX_OPEN_HOURS = 18;

    private final SystemConfigService config;

    public AntiPassbackPolicy(SystemConfigService config) {
        this.config = config;
    }

    @Override
    public Optional<Denial> evaluate(ScanContext context) {
        AttendanceRecord last = context.lastRecord();
        Duration cooldown = Duration.ofSeconds(
                config.getInt("antiPassback.cooldownSeconds", DEFAULT_COOLDOWN_SECONDS));
        Duration maxOpen = Duration.ofHours(
                config.getInt("antiPassback.maxOpenHours", DEFAULT_MAX_OPEN_HOURS));

        if (last == null) {
            return context.direction() == Direction.OUT
                    ? Optional.of(new Denial("NO_CHECK_IN",
                            "Can't check out: this person has not checked in yet. Scan at an IN gate first."))
                    : Optional.empty();
        }

        Duration sinceLast = Duration.between(last.getScannedAt(), context.scannedAt());
        if (sinceLast.isNegative()) {
            return Optional.empty();
        }

        if (last.getDirection() == context.direction()
                && sinceLast.compareTo(cooldown) < 0) {
            return Optional.of(new Denial("DUPLICATE_SCAN",
                    "Already checked " + last.getDirection().name()
                            + " less than " + cooldown.toMinutes() + " minutes ago (anti-passback: one QR, one person)."));
        }

        if (context.direction() == Direction.OUT) {
            if (last.getDirection() == Direction.OUT) {
                return Optional.of(new Denial("NO_CHECK_IN",
                        "Can't check out: this person is already checked out. Scan at an IN gate to check in."));
            }
            if (sinceLast.compareTo(maxOpen) > 0) {
                return Optional.of(new Denial("CHECK_IN_TOO_OLD",
                        "Can't check out: the last check-in is more than " + maxOpen.toHours()
                        + " hours old. Ask HR to fix the record."));
            }
        }

        return Optional.empty();
    }
}
