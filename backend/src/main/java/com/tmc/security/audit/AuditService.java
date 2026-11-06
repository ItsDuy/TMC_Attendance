package com.tmc.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmc.security.api.AuditPort;
import com.tmc.shared.crypto.HmacUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AuditService implements AuditPort {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final String GENESIS =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private final AuditLogRepository auditLogRepository;
    private final AuditChainHeadRepository headRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository,
                        AuditChainHeadRepository headRepository,
                        ObjectMapper objectMapper,
                        Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.headRepository = headRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(String actor, String action, String resourceType, String resourceId,
                       Object payload) {
        Instant occurredAt = clock.instant();
        String payloadHash = HmacUtil.sha256Hex(serialize(payload));

        AuditChainHead head = headRepository.lockHead().orElseThrow(
                () -> new IllegalStateException("Audit chain head is missing"));
        String prevHash = head.getLastHash();
        String hash = HmacUtil.sha256Hex(canonical(
                prevHash, occurredAt, actor, action, resourceType, resourceId, payloadHash));

        auditLogRepository.save(new AuditLog(
                occurredAt, actor, action, resourceType, resourceId, payloadHash, prevHash, hash));

        head.setLastHash(hash);
        headRepository.save(head);

        log.debug("Audit {} {} by {}", action, resourceId, actor);
    }

    @Transactional(readOnly = true)
    public ChainVerification verifyChain() {
        List<AuditLog> entries = auditLogRepository.findAllByOrderByIdAsc();
        String prev = GENESIS;
        long checked = 0;
        for (AuditLog entry : entries) {
            if (!entry.getPrevHash().equals(prev)) {
                return new ChainVerification(false, checked, entry.getId(), "PREV_HASH_MISMATCH");
            }
            String expected = HmacUtil.sha256Hex(canonical(
                    entry.getPrevHash(), entry.getOccurredAt(), entry.getActor(), entry.getAction(),
                    entry.getResourceType(), entry.getResourceId(), entry.getPayloadHash()));
            if (!expected.equals(entry.getHash())) {
                return new ChainVerification(false, checked, entry.getId(), "HASH_MISMATCH");
            }
            prev = entry.getHash();
            checked++;
        }
        return new ChainVerification(true, checked, null, null);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent(int page, int size) {
        return auditLogRepository.findAllByOrderByIdDesc(PageRequest.of(page, size)).getContent();
    }

    private String serialize(Object payload) {
        if (payload == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return String.valueOf(payload);
        }
    }

    private static String canonical(String prevHash, Instant occurredAt, String actor, String action,
                                    String resourceType, String resourceId, String payloadHash) {
        return String.join("|",
                prevHash,
                Long.toString(occurredAt.toEpochMilli()),
                actor,
                action,
                resourceType,
                resourceId == null ? "" : resourceId,
                payloadHash);
    }

    public record ChainVerification(boolean valid, long checked, Long brokenAt, String reason) {
    }
}
