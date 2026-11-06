package com.tmc.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row (ADR-006). Written in the same transaction as the
 * business change so a crash can never drop the downstream notification.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(String eventType, String aggregateType, String aggregateId,
                                      String payload, Instant now) {
        OutboxEvent event = new OutboxEvent();
        event.eventType = eventType;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.createdAt = now;
        event.nextAttemptAt = now;
        return event;
    }

    public void markSent(Instant now) {
        this.status = OutboxStatus.SENT;
        this.sentAt = now;
        this.attempts++;
        this.lastError = null;
    }

    public void markFailed(String error, Instant nextAttempt, int maxAttempts) {
        this.attempts++;
        this.lastError = truncate(error);
        if (attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.status = OutboxStatus.PENDING;
            this.nextAttemptAt = nextAttempt;
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 400 ? value.substring(0, 400) : value;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
