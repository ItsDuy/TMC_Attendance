package com.tmc.attendance.core;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.Direction;
import com.tmc.gate.domain.GateDirection;
import com.tmc.gate.api.KioskView;
import org.springframework.stereotype.Component;

@Component
public class DirectionResolver {

    public Direction resolve(KioskView kiosk, Direction requested, AttendanceRecord lastRecord) {
        if (kiosk.gateDirection() == GateDirection.IN) {
            return Direction.IN;
        }
        if (kiosk.gateDirection() == GateDirection.OUT) {
            return Direction.OUT;
        }
        if (requested != null) {
            return requested;
        }
        if (lastRecord == null || lastRecord.getDirection() == Direction.OUT) {
            return Direction.IN;
        }
        return Direction.OUT;
    }
}
