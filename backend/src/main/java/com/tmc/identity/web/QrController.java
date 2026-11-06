package com.tmc.identity.web;

import com.tmc.identity.api.QrResponse;
import com.tmc.identity.api.QrTokenPort;
import com.tmc.security.api.SecurityUtils;
import com.tmc.shared.config.TmcProperties;
import com.tmc.shared.error.DomainException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class QrController {

    private final QrTokenPort qrTokenPort;
    private final TmcProperties properties;
    private final Clock clock;

    public QrController(QrTokenPort qrTokenPort, TmcProperties properties, Clock clock) {
        this.qrTokenPort = qrTokenPort;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("/me/qr")
    @PreAuthorize("isAuthenticated()")
    public QrResponse myQr(Authentication authentication) {
        UUID employeeId = SecurityUtils.user(authentication).employeeId();
        if (employeeId == null) {
            throw DomainException.badRequest("NO_EMPLOYEE_LINK",
                    "This account is not linked to an employee record");
        }
        return mint(employeeId);
    }

    @GetMapping("/employees/{id}/qr")
    @PreAuthorize("hasRole('ADMIN')")
    public QrResponse employeeQr(@PathVariable UUID id) {
        return mint(id);
    }

    private QrResponse mint(UUID employeeId) {
        String token = qrTokenPort.mint(employeeId);
        long ttl = properties.security().qrTokenTtl().toSeconds();
        return new QrResponse(employeeId, token, "TMC1", ttl,
                clock.instant().plusSeconds(ttl));
    }
}
