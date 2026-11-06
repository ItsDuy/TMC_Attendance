package com.tmc.identity.api;

import java.time.Instant;
import java.util.UUID;

public record QrResponse(
        UUID employeeId,
        String token,
        String tokenFormat,
        long ttlSeconds,
        Instant expiresAt
) {
}
