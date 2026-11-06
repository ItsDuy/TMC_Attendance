package com.tmc.shared.outbox;

/**
 * Extension point for downstream consumers of outbox events. Domain modules may
 * register handlers without the publisher knowing about them (OCP).
 */
public interface OutboxEventHandler {

    boolean supports(String eventType);

    void handle(OutboxEvent event) throws Exception;
}
