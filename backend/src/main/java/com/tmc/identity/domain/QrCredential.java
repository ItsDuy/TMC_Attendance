package com.tmc.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "qr_credentials")
public class QrCredential {

    @Id
    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialStatus status;

    @Column(name = "rotated_at", nullable = false)
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected QrCredential() {
    }

    public QrCredential(UUID employeeId) {
        this.employeeId = employeeId;
        this.version = 1;
        this.status = CredentialStatus.ACTIVE;
        this.rotatedAt = Instant.now();
    }

    public void rotate(Instant now) {
        this.version++;
        this.status = CredentialStatus.ACTIVE;
        this.rotatedAt = now;
        this.revokedAt = null;
    }

    public void revoke(Instant now) {
        this.status = CredentialStatus.REVOKED;
        this.revokedAt = now;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public int getVersion() {
        return version;
    }

    public CredentialStatus getStatus() {
        return status;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
