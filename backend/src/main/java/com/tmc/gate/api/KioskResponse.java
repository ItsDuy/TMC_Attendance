package com.tmc.gate.api;

import com.tmc.gate.domain.KioskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record KioskResponse(
        UUID id,
        UUID gateId,
        UUID factoryId,
        String name,
        KioskStatus status,
        int configVersion,
        Instant lastHeartbeatAt,
        String secret
) {
    public record CreateKioskRequest(@NotNull UUID gateId, @NotBlank String name) {
    }
}
