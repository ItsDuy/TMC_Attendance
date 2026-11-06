package com.tmc.attendance.web;

import com.tmc.attendance.domain.AttendanceStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final Clock clock;

    public AttendanceController(AttendanceQueryService queryService, Clock clock) {
        this.queryService = queryService;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR','HR','SECURITY','ADMIN','EXECUTIVE')")
    public List<AttendanceQueryService.AttendanceRecordView> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID factoryId) {
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(1)) : from;
        return queryService.search(start, end, employeeId, factoryId);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPERVISOR','SECURITY','ADMIN')")
    public List<AttendanceQueryService.AttendanceRecordView> pending() {
        return queryService.byStatus(AttendanceStatus.PENDING);
    }

    @GetMapping("/gate-unavailable")
    @PreAuthorize("hasAnyRole('SUPERVISOR','SECURITY','ADMIN')")
    public List<AttendanceQueryService.AttendanceRecordView> gateUnavailable() {
        return queryService.byStatus(AttendanceStatus.GATE_UNAVAILABLE);
    }
}
