package com.tmc.attendance.core;

import com.tmc.attendance.domain.Direction;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything a kiosk tells the backend about one scan. The kiosk id comes from
 * the authenticated principal, never from this payload.
 */
public record ScanCommand(
        UUID kioskId,
        UUID clientEventId,
        String token,
        Direction requestedDirection,
        Instant scannedAt,
        boolean offline
) {
}
