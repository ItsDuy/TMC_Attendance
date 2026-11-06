package com.tmc.gate.web;

import com.tmc.gate.api.KioskResponse;
import com.tmc.gate.api.KioskService;
import com.tmc.security.api.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/kiosks")
@PreAuthorize("hasRole('ADMIN')")
public class KioskController {

    private final KioskService kioskService;

    public KioskController(KioskService kioskService) {
        this.kioskService = kioskService;
    }

    @GetMapping
    public List<KioskResponse> list(@RequestParam(required = false) UUID factoryId) {
        return kioskService.list(factoryId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KioskResponse create(@Valid @RequestBody KioskResponse.CreateKioskRequest request,
                                Authentication authentication) {
        return kioskService.create(request.gateId(), request.name(),
                SecurityUtils.actor(authentication));
    }

    @PostMapping("/{id}/rotate-secret")
    public KioskResponse rotateSecret(@PathVariable UUID id, Authentication authentication) {
        return kioskService.rotateSecret(id, SecurityUtils.actor(authentication));
    }
}
