package com.tmc.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tmc")
public record TmcProperties(Security security, Turnstile turnstile, Seed seed) {

    public record Security(
            String jwtSecret,
            Duration jwtTtl,
            String qrSecret,
            String dataKey,
            Duration kioskClockSkew,
            Duration qrTokenTtl
    ) {
    }

    public record Turnstile(String mode, String url, Duration timeout) {
    }

    public record Seed(boolean enabled) {
    }
}
