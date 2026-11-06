package com.tmc.gate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kiosks")
public class Kiosk {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "gate_id", nullable = false)
    private UUID gateId;

    @Column(name = "factory_id", nullable = false)
    private UUID factoryId;

    @Column(nullable = false)
    private String name;

    @Column(name = "secret_enc", nullable = false)
    private String secretEnc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KioskStatus status;

    @Column(name = "config_version", nullable = false)
    private int configVersion;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Kiosk() {
    }

    public Kiosk(UUID gateId, UUID factoryId, String name, String secretEnc) {
        this.gateId = gateId;
        this.factoryId = factoryId;
        this.name = name;
        this.secretEnc = secretEnc;
        this.status = KioskStatus.ACTIVE;
        this.configVersion = 1;
        this.createdAt = Instant.now();
    }

    public void heartbeat(Instant at) {
        this.lastHeartbeatAt = at;
    }

    public void rotateSecret(String secretEnc) {
        this.secretEnc = secretEnc;
        this.configVersion++;
    }

    public void setStatus(KioskStatus status) {
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGateId() {
        return gateId;
    }

    public UUID getFactoryId() {
        return factoryId;
    }

    public String getName() {
        return name;
    }

    public String getSecretEnc() {
        return secretEnc;
    }

    public KioskStatus getStatus() {
        return status;
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }
}
