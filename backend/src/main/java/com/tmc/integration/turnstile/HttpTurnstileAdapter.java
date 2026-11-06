package com.tmc.integration.turnstile;

import com.tmc.attendance.api.GateOutcome;
import com.tmc.attendance.api.TurnstilePort;
import com.tmc.shared.config.TmcProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Real HTTP adapter for the vendor turnstile API (ADR-003). Timeouts are applied
 * here as well as in the resilience decorator so a hung socket cannot pin a
 * request thread.
 */
public class HttpTurnstileAdapter implements TurnstilePort {

    private final RestClient client;

    public HttpTurnstileAdapter(TmcProperties properties) {
        Duration timeout = properties.turnstile().timeout();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.client = RestClient.builder()
                .baseUrl(properties.turnstile().url())
                .requestFactory(factory)
                .build();
    }

    @Override
    public GateOutcome open(String gateId) {
        long start = System.nanoTime();
        client.post()
                .uri("/gates/{gateId}/open", gateId)
                .retrieve()
                .toBodilessEntity();
        return GateOutcome.success("HTTP_OPEN", StubTurnstileAdapter.elapsedMillis(start));
    }
}
