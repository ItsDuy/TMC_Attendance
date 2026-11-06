package com.tmc.attendance.web;

import com.tmc.attendance.core.ManualEntryService;
import com.tmc.attendance.core.ScanResult;
import com.tmc.security.api.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual entry by a security guard: the human fallback when a phone, kiosk or network is down.
 * Only the guard role may use it; every entry is audited and flagged for HR review.
 */
@RestController
@RequestMapping("/api/attendance/manual")
@PreAuthorize("hasRole('SECURITY')")
public class ManualEntryController {

    private final ManualEntryService manualEntries;

    public ManualEntryController(ManualEntryService manualEntries) {
        this.manualEntries = manualEntries;
    }

    /** Step 1: the guard types the code from the ID card and checks the name and current state. */
    @GetMapping("/check")
    public ManualEntryService.EmployeeCheck check(@RequestParam String code) {
        return manualEntries.check(code);
    }

    /** Step 2: record the entry, with a reason. Same access rules as a kiosk scan. */
    @PostMapping
    public ScanResult enter(@RequestBody ManualEntryService.ManualEntryCommand command,
                            Authentication authentication) {
        return manualEntries.enter(command, SecurityUtils.actor(authentication));
    }
}
