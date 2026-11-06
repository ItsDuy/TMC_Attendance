package com.tmc.security.audit;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEntryResponse> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        return auditService.recent(page, Math.min(size, 200)).stream()
                .map(AuditEntryResponse::from)
                .toList();
    }

    @GetMapping("/verify")
    public AuditService.ChainVerification verify() {
        return auditService.verifyChain();
    }

    public record AuditEntryResponse(Long id, Instant occurredAt, String actor, String action,
                                     String resourceType, String resourceId, String hash) {
        static AuditEntryResponse from(AuditLog log) {
            return new AuditEntryResponse(log.getId(), log.getOccurredAt(), log.getActor(),
                    log.getAction(), log.getResourceType(), log.getResourceId(), log.getHash());
        }
    }
}
