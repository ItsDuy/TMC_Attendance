package com.tmc.attendance.web;

import com.tmc.attendance.domain.AttendanceStatus;
import com.tmc.identity.api.AccessScope;
import com.tmc.identity.api.AccessScopeService;
import com.tmc.security.api.SecurityUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceQueryService queryService;
    private final AccessScopeService scopes;
    private final Clock clock;

    public AttendanceController(AttendanceQueryService queryService, AccessScopeService scopes, Clock clock) {
        this.queryService = queryService;
        this.scopes = scopes;
        this.clock = clock;
    }

    /**
     * Named scan records. Executive management is not listed: it sees head counts only.
     * A supervisor gets their own team only.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR','HR','SECURITY','ADMIN')")
    public List<AttendanceQueryService.AttendanceRecordView> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID factoryId,
            Authentication authentication) {
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(1)) : from;
        AccessScope scope = scopes.scopeFor(SecurityUtils.user(authentication));
        return queryService.search(start, end, employeeId, factoryId, scope);
    }

    /** Gate operations belong to security (admin may watch). */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SECURITY','ADMIN')")
    public List<AttendanceQueryService.AttendanceRecordView> pending() {
        return queryService.byStatus(AttendanceStatus.PENDING);
    }

    @GetMapping("/gate-unavailable")
    @PreAuthorize("hasAnyRole('SECURITY','ADMIN')")
    public List<AttendanceQueryService.AttendanceRecordView> gateUnavailable() {
        return queryService.byStatus(AttendanceStatus.GATE_UNAVAILABLE);
    }
}
