package com.tmc.attendance.api;

public interface TurnstilePort {

    GateOutcome open(String gateId);
}
