package com.tmc.attendance.core;

import com.tmc.gate.api.KioskView;
import com.tmc.gate.domain.GateDirection;

import java.util.UUID;

/**
 * Where an access decision is made: a gate, its factory and its direction, plus who operates it.
 * Access policies need only this, not a whole kiosk (Interface Segregation), so a kiosk scan and a
 * guard's manual entry are judged by exactly the same rules (Liskov: either kind of access point
 * can be used wherever the policies expect one).
 */
public record AccessPoint(UUID gateId, UUID factoryId, GateDirection gateDirection, String operatedBy) {

    public static AccessPoint kiosk(KioskView kiosk) {
        return new AccessPoint(kiosk.gateId(), kiosk.factoryId(), kiosk.gateDirection(), "kiosk:" + kiosk.id());
    }

    public static AccessPoint guard(UUID gateId, UUID factoryId, GateDirection gateDirection, String guard) {
        return new AccessPoint(gateId, factoryId, gateDirection, "guard:" + guard);
    }
}
