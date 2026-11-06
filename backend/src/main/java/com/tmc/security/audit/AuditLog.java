package com.tmc.security.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only audit row. {@code hash} covers the previous row's hash, so any
 * retrospective edit breaks the chain (ADR-018).
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @Column(name = "prev_hash", nullable = false)
    private String prevHash;

    @Column(nullable = false)
    private String hash;

    protected AuditLog() {
    }

    public AuditLog(Instant occurredAt, String actor, String action, String resourceType,
                    String resourceId, String payloadHash, String prevHash, String hash) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payloadHash = payloadHash;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getHash() {
        return hash;
    }
}
