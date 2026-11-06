package com.tmc.integration.turnstile;

import com.tmc.attendance.api.GateOutcome;
import com.tmc.attendance.api.TurnstilePort;

/**
 * Always-failing adapter used by the demo and tests to prove that a broken gate
 * never loses the attendance record and that the manual fallback works.
 */
public class ChaosTurnstileAdapter implements TurnstilePort {

    @Override
    public GateOutcome open(String gateId) {
        throw new TurnstileUnavailableException("Simulated turnstile outage for gate " + gateId);
    }
}
