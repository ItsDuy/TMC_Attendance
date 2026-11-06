package com.tmc.identity.api;

import java.time.Instant;
import java.util.UUID;

public record QrClaims(UUID employeeId, String jti, Instant expiresAt, int version) {
}
