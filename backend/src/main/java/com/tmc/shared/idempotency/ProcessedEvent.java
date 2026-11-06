package com.tmc.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per kiosk-generated {@code clientEventId}. A primary-key collision is
 * the idempotency guarantee for retries (ADR-007).
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent implements Persistable<UUID> {

    @Id
    @Column(name = "client_event_id")
    private UUID clientEventId;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "result_json", nullable = false)
    private String resultJson;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID clientEventId, UUID recordId, String resultJson, Instant processedAt) {
        this.clientEventId = clientEventId;
        this.recordId = recordId;
        this.resultJson = resultJson;
        this.processedAt = processedAt;
    }

    @Override
    public UUID getId() {
        return clientEventId;
    }

    @Override
    public boolean isNew() {
        return true;
    }

    public UUID getClientEventId() {
        return clientEventId;
    }

    public UUID getRecordId() {
        return recordId;
    }

    public String getResultJson() {
        return resultJson;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
