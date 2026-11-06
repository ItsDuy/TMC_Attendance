package com.tmc.attendance.web;

import com.tmc.attendance.api.GateOutcome;
import com.tmc.attendance.api.TurnstilePort;
import com.tmc.attendance.core.AttendanceRecorder;
import com.tmc.security.api.AuditPort;
import com.tmc.security.api.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manual fallback for a failed turnstile (ADR-009). The attendance record stays
 * visible in the recovery queue either way.
 */
@RestController
@RequestMapping("/api/gates")
public class ManualGateController {

    private final TurnstilePort turnstile;
    private final AttendanceRecorder recorder;
    private final AuditPort audit;

    public ManualGateController(TurnstilePort turnstile, AttendanceRecorder recorder,
                                AuditPort audit) {
        this.turnstile = turnstile;
        this.recorder = recorder;
        this.audit = audit;
    }

    @PostMapping("/{gateId}/open")
    @PreAuthorize("hasAnyRole('SECURITY','ADMIN')")
    public Map<String, Object> open(@PathVariable UUID gateId,
                                    @RequestParam(required = false) UUID recordId,
                                    Authentication authentication) {
        long start = System.nanoTime();
        boolean opened;
        String detail;
        try {
            GateOutcome outcome = turnstile.open(gateId.toString());
            opened = outcome.opened();
            detail = outcome.detail();
        } catch (RuntimeException e) {
            opened = false;
            detail = e.getMessage() == null ? "Turnstile unavailable" : e.getMessage();
        }

        boolean resolved = false;
        if (opened && recordId != null) {
            recorder.resolveManually(recordId);
            resolved = true;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gateId", gateId);
        payload.put("recordResolved", resolved);
        payload.put("opened", opened);
        payload.put("detail", detail);
        payload.put("latencyMillis", (System.nanoTime() - start) / 1_000_000);

        audit.record(SecurityUtils.actor(authentication), "GATE_OPENED_MANUALLY", "Gate",
                gateId.toString(), Map.of("opened", opened, "recordResolved", resolved));

        return payload;
    }

    /**
     * For when the turnstile is down and the guard physically admits the employee:
     * confirms the recovery-queue record so it is not lost from reports.
     */
    @PostMapping("/records/{recordId}/resolve")
    @PreAuthorize("hasAnyRole('SECURITY','ADMIN')")
    public Map<String, Object> resolve(@PathVariable UUID recordId, Authentication authentication) {
        recorder.resolveManually(recordId);
        audit.record(SecurityUtils.actor(authentication), "RECORD_RESOLVED_MANUALLY", "AttendanceRecord",
                recordId.toString(), Map.of("gateOutcome", "MANUAL_OVERRIDE"));
        return Map.of("recordId", recordId, "status", "COMPLETED");
    }
}
