package com.tmc.attendance.api;

public record GateOutcome(boolean opened, String detail, long latencyMillis) {

    public static GateOutcome success(String detail, long latencyMillis) {
        return new GateOutcome(true, detail, latencyMillis);
    }

    public static GateOutcome failure(String detail, long latencyMillis) {
        return new GateOutcome(false, detail, latencyMillis);
    }
}
