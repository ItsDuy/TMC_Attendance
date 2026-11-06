package com.tmc.integration.turnstile;

import com.tmc.attendance.api.TurnstilePort;
import com.tmc.shared.config.TmcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TurnstileConfig {

    private static final Logger log = LoggerFactory.getLogger(TurnstileConfig.class);

    /**
     * Selects the delegate from {@code tmc.turnstile.mode} and always wraps it in
     * the timeout/circuit-breaker decorator.
     */
    @Bean(destroyMethod = "close")
    public ResilientTurnstileAdapter turnstilePort(TmcProperties properties) {
        String mode = properties.turnstile().mode();
        TurnstilePort delegate = switch (mode) {
            case "http" -> new HttpTurnstileAdapter(properties);
            case "chaos" -> new ChaosTurnstileAdapter();
            default -> new StubTurnstileAdapter();
        };
        log.info("Turnstile adapter mode={} delegate={}", mode, delegate.getClass().getSimpleName());
        return new ResilientTurnstileAdapter(delegate, properties.turnstile().timeout());
    }
}
