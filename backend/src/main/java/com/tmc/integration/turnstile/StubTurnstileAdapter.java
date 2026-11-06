package com.tmc.integration.turnstile;

import com.tmc.attendance.api.GateOutcome;
import com.tmc.attendance.api.TurnstilePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default adapter: pretends the gate opened. Keeps the whole system runnable
 * without real hardware, which is what the demo and tests use.
 */
public class StubTurnstileAdapter implements TurnstilePort {

    private static final Logger log = LoggerFactory.getLogger(StubTurnstileAdapter.class);

    @Override
    public GateOutcome open(String gateId) {
        long start = System.nanoTime();
        log.info("Stub turnstile opening gate {}", gateId);
        return GateOutcome.success("STUB_OPEN", elapsedMillis(start));
    }

    static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
