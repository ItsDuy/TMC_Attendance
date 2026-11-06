package com.tmc.attendance.web;

import com.tmc.attendance.core.ScanCommand;
import com.tmc.attendance.core.ScanOrchestrator;
import com.tmc.attendance.core.ScanResult;
import com.tmc.attendance.domain.Direction;
import com.tmc.security.kiosk.KioskPrincipal;
import com.tmc.shared.error.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/kiosk")
@PreAuthorize("hasRole('KIOSK')")
public class KioskScanController {

    private static final Duration MAX_OFFLINE_AGE = Duration.ofDays(7);
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final ScanOrchestrator orchestrator;
    private final Clock clock;

    public KioskScanController(ScanOrchestrator orchestrator, Clock clock) {
        this.orchestrator = orchestrator;
        this.clock = clock;
    }

    @PostMapping("/scans")
    public ScanResult scan(@Valid @RequestBody ScanRequest request, Authentication authentication) {
        UUID kioskId = ((KioskPrincipal) authentication.getPrincipal()).kioskId();
        Instant scannedAt = clock.instant();
        return orchestrator.scan(new ScanCommand(
                kioskId, eventId(request.clientEventId()), request.token(),
                request.direction(), scannedAt, false));
    }

    /**
     * Replays a buffered batch after a network outage (ADR-008). Each event keeps
     * its original scan time; results are reported per event so the kiosk can
     * surface rejects without dropping data.
     */
    @PostMapping("/scans/sync")
    public SyncResponse sync(@Valid @RequestBody SyncRequest request,
                            Authentication authentication) {
        UUID kioskId = ((KioskPrincipal) authentication.getPrincipal()).kioskId();
        Instant now = clock.instant();
        List<ScanResult> results = new ArrayList<>(request.events().size());
        for (ScanRequest event : request.events()) {
            Instant scannedAt = event.scannedAt() == null ? now : event.scannedAt();
            if (scannedAt.isAfter(now.plus(MAX_FUTURE_SKEW))) {
                throw DomainException.badRequest("SCAN_IN_FUTURE",
                        "Buffered scan time is in the future: " + scannedAt);
            }
            if (scannedAt.isBefore(now.minus(MAX_OFFLINE_AGE))) {
                throw DomainException.badRequest("SCAN_TOO_OLD",
                        "Buffered scan is older than the retention window: " + scannedAt);
            }
            results.add(orchestrator.scan(new ScanCommand(
                    kioskId, eventId(event.clientEventId()), event.token(),
                    event.direction(), scannedAt, true)));
        }
        return new SyncResponse(results);
    }

    private static UUID eventId(UUID requested) {
        return requested == null ? UUID.randomUUID() : requested;
    }

    public record ScanRequest(UUID clientEventId,
                              @NotBlank String token,
                              Direction direction,
                              Instant scannedAt) {
    }

    public record SyncRequest(@NotEmpty List<@Valid ScanRequest> events) {
    }

    public record SyncResponse(List<ScanResult> results) {
    }
}
