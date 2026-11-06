package com.tmc.gate.api;

import com.tmc.gate.domain.GateDirection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Narrow read port for monitoring: every gate with its direction and when a kiosk on it last checked in. */
public interface GateStatusLookup {

    List<GateStatusView> gateStatuses();

    /** {@code lastHeartbeatAt} is the newest heartbeat of any kiosk on the gate, or null if none ever. */
    record GateStatusView(UUID gateId, UUID factoryId, String name, GateDirection direction,
                          Instant lastHeartbeatAt) {
    }
}
