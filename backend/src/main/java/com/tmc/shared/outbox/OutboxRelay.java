package com.tmc.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Relays outbox rows. Each row is delivered at least once; consumers must be
 * idempotent. In a full deployment this is where a broker producer would plug in.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxRepository repository;
    private final List<OutboxEventHandler> handlers;
    private final Clock clock;

    public OutboxRelay(OutboxRepository repository, List<OutboxEventHandler> handlers, Clock clock) {
        this.repository = repository;
        this.handlers = handlers;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tmc.outbox.relay-interval-ms:2000}")
    @Transactional
    public void relay() {
        Instant now = clock.instant();
        for (OutboxEvent event : repository.findDue(now, PageRequest.of(0, BATCH_SIZE))) {
            try {
                for (OutboxEventHandler handler : handlers) {
                    if (handler.supports(event.getEventType())) {
                        handler.handle(event);
                    }
                }
                event.markSent(now);
                log.debug("Outbox event {} ({}) delivered", event.getId(), event.getEventType());
            } catch (Exception e) {
                Instant retryAt = now.plus(Duration.ofSeconds(5L * Math.max(1, event.getAttempts() + 1)));
                event.markFailed(e.getMessage(), retryAt, MAX_ATTEMPTS);
                log.warn("Outbox event {} failed (attempt {}): {}",
                        event.getId(), event.getAttempts(), e.getMessage());
            }
        }
    }

    public long pendingCount() {
        return repository.count();
    }
}
