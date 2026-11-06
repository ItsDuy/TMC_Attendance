package com.tmc.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hr_export_batches")
public class HrExportBatch {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String period;

    @Column(name = "factory_id", nullable = false)
    private UUID factoryId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(nullable = false)
    private String checksum;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HrExportBatch() {
    }

    public HrExportBatch(String period, UUID factoryId, String idempotencyKey, int recordCount,
                         String checksum, String status, Instant createdAt) {
        this.period = period;
        this.factoryId = factoryId;
        this.idempotencyKey = idempotencyKey;
        this.recordCount = recordCount;
        this.checksum = checksum;
        this.status = status;
        this.attempts = 1;
        this.createdAt = createdAt;
        if (SENT.equals(status)) {
            this.sentAt = createdAt;
        }
    }

    public static HrExportBatch firstAttempt(String period, UUID factoryId, String key,
                                             int recordCount, String checksum,
                                             boolean accepted, String error, Instant at) {
        HrExportBatch batch = new HrExportBatch(period, factoryId, key, recordCount, checksum,
                FAILED, at);
        batch.applyOutcome(accepted, error, at);
        return batch;
    }

    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    public boolean isFailed() {
        return FAILED.equals(status);
    }

    /** Records the outcome of a delivery attempt on a batch that already exists. */
    public void recordAttempt(int recordCount, String checksum, boolean accepted,
                              String error, Instant at) {
        this.attempts++;
        this.recordCount = recordCount;
        this.checksum = checksum;
        applyOutcome(accepted, error, at);
    }

    void applyOutcome(boolean accepted, String error, Instant at) {
        if (accepted) {
            this.status = SENT;
            this.sentAt = at;
            this.lastError = null;
        } else {
            this.status = FAILED;
            this.lastError = error == null ? "Unknown error"
                    : error.substring(0, Math.min(error.length(), 400));
        }
    }

    public UUID getId() {
        return id;
    }

    public String getPeriod() {
        return period;
    }

    public UUID getFactoryId() {
        return factoryId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
