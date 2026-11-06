package com.tmc.identity.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Contract for dynamic QR tokens (ADR-014). Tokens are short-lived HMAC-signed
 * payloads containing no personal data.
 */
public interface QrTokenPort {

    String mint(UUID employeeId);

    QrClaims verify(String token, Instant at);

    /**
     * Per-employee derivation key, provisioned to kiosks so they can verify a
     * token offline (ADR-016).
     */
    String derivedKey(UUID employeeId);
}
