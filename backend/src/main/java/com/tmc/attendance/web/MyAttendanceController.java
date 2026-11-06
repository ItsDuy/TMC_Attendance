package com.tmc.attendance.web;

import com.tmc.security.api.SecurityUtils;
import com.tmc.shared.error.DomainException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * "My attendance": every account that belongs to an employee (employee, supervisor, HR) can see
 * its own scans for a month, and only its own.
 */
@RestController
@RequestMapping("/api/me/attendance")
public class MyAttendanceController {

    private final AttendanceQueryService queryService;

    public MyAttendanceController(AttendanceQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<AttendanceQueryService.AttendanceRecordView> mine(@RequestParam String period,
                                                                  Authentication authentication) {
        UUID employeeId = SecurityUtils.user(authentication).employeeId();
        if (employeeId == null) {
            throw DomainException.badRequest("NO_EMPLOYEE_LINK",
                    "This account is not linked to an employee record");
        }
        YearMonth month = YearMonth.parse(period);
        // Same month boundaries as the monthly report, so both always agree.
        Instant from = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return queryService.search(from, to, employeeId, null);
    }
}
