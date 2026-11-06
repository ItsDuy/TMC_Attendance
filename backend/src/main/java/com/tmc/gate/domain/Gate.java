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
@Table(name = "gates")
public class Gate {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "factory_id", nullable = false)
    private UUID factoryId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GateDirection direction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Gate() {
    }

    public Gate(UUID factoryId, String name, GateDirection direction) {
        this.factoryId = factoryId;
        this.name = name;
        this.direction = direction;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getFactoryId() {
        return factoryId;
    }

    public String getName() {
        return name;
    }

    public GateDirection getDirection() {
        return direction;
    }
}
