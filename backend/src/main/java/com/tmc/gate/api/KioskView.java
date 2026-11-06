package com.tmc.gate.api;

import com.tmc.gate.domain.GateDirection;
import com.tmc.gate.domain.KioskStatus;

import java.util.UUID;

public record KioskView(
        UUID id,
        UUID gateId,
        UUID factoryId,
        String name,
        GateDirection gateDirection,
        KioskStatus status
) {
}
