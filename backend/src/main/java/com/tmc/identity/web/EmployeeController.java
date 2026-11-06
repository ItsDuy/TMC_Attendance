package com.tmc.identity.web;

import com.tmc.identity.api.AccessScopeService;
import com.tmc.identity.api.CreateEmployeeRequest;
import com.tmc.identity.api.EmployeeResponse;
import com.tmc.identity.api.EmployeeService;
import com.tmc.identity.api.UpdateEmployeeRequest;
import com.tmc.identity.domain.QrCredential;
import com.tmc.security.api.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AccessScopeService scopes;

    public EmployeeController(EmployeeService employeeService, AccessScopeService scopes) {
        this.employeeService = employeeService;
        this.scopes = scopes;
    }

    /** Who and how much detail the viewer gets is decided by {@link AccessScopeService}, not here. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public List<EmployeeResponse> list(@RequestParam(required = false) UUID factoryId,
                                       Authentication authentication) {
        return employeeService.list(factoryId, scopes.scopeFor(SecurityUtils.user(authentication)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HR')")
    public EmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest request,
                                   Authentication authentication) {
        return employeeService.create(request, SecurityUtils.actor(authentication));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('HR')")
    public EmployeeResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateEmployeeRequest request,
                                   Authentication authentication) {
        return employeeService.update(id, request, SecurityUtils.actor(authentication));
    }

    @PostMapping("/{id}/qr/rotate")
    @PreAuthorize("hasRole('HR')")
    public CredentialResponse rotate(@PathVariable UUID id, Authentication authentication) {
        QrCredential credential = employeeService.rotate(id, SecurityUtils.actor(authentication));
        return CredentialResponse.from(credential);
    }

    @PostMapping("/{id}/qr/revoke")
    @PreAuthorize("hasRole('HR')")
    public CredentialResponse revoke(@PathVariable UUID id, Authentication authentication) {
        QrCredential credential = employeeService.revoke(id, SecurityUtils.actor(authentication));
        return CredentialResponse.from(credential);
    }

    public record CredentialResponse(UUID employeeId, int version, String status, Instant rotatedAt) {
        static CredentialResponse from(QrCredential credential) {
            return new CredentialResponse(credential.getEmployeeId(), credential.getVersion(),
                    credential.getStatus().name(), credential.getRotatedAt());
        }
    }
}
