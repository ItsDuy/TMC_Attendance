package com.tmc.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "gate_id", nullable = false)
    private UUID gateId;

    @Column(name = "kiosk_id", nullable = false)
    private UUID kioskId;

    @Column(name = "factory_id", nullable = false)
    private UUID factoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    @Column(name = "gate_outcome")
    private String gateOutcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSource source;

    @Column(name = "reason")
    private String reason;

    @Transient
    private boolean isNew = true;

    protected AttendanceRecord() {
    }

    public AttendanceRecord(UUID employeeId, UUID gateId, UUID kioskId, UUID factoryId,
                            Direction direction, Instant scannedAt, Instant recordedAt,
                            EventSource source) {
        this.id = UUID.randomUUID();
        this.employeeId = employeeId;
        this.gateId = gateId;
        this.kioskId = kioskId;
        this.factoryId = factoryId;
        this.direction = direction;
        this.scannedAt = scannedAt;
        this.recordedAt = recordedAt;
        this.status = AttendanceStatus.PENDING;
        this.source = source;
    }

    public void complete(String gateOutcome) {
        this.status = AttendanceStatus.COMPLETED;
        this.gateOutcome = gateOutcome;
        this.reason = null;
    }

    public void gateUnavailable(String reason) {
        this.status = AttendanceStatus.GATE_UNAVAILABLE;
        this.gateOutcome = "GATE_UNAVAILABLE";
        this.reason = reason;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    void markPersisted() {
        this.isNew = false;
    }

    @PostLoad
    void markLoaded() {
        this.isNew = false;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getGateId() {
        return gateId;
    }

    public UUID getKioskId() {
        return kioskId;
    }

    public UUID getFactoryId() {
        return factoryId;
    }

    public Direction getDirection() {
        return direction;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public String getGateOutcome() {
        return gateOutcome;
    }

    public EventSource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }
}
