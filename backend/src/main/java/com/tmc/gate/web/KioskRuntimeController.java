package com.tmc.gate.web;

import com.tmc.gate.api.KioskService;
import com.tmc.gate.api.ProvisioningBundle;
import com.tmc.security.kiosk.KioskPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Kiosk-facing endpoints. Reached through the HMAC filter chain, so the kiosk id
 * is taken from the authenticated principal, never from the request body.
 */
@RestController
@RequestMapping("/api/kiosk")
@PreAuthorize("hasRole('KIOSK')")
public class KioskRuntimeController {

    private final KioskService kioskService;

    public KioskRuntimeController(KioskService kioskService) {
        this.kioskService = kioskService;
    }

    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(Authentication authentication) {
        UUID kioskId = ((KioskPrincipal) authentication.getPrincipal()).kioskId();
        kioskService.heartbeat(kioskId);
        return Map.of("kioskId", kioskId, "status", "OK");
    }

    @PostMapping("/provision")
    public ProvisioningBundle provision(Authentication authentication) {
        UUID kioskId = ((KioskPrincipal) authentication.getPrincipal()).kioskId();
        return kioskService.provision(kioskId);
    }
}
