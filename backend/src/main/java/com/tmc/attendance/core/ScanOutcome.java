package com.tmc.attendance.core;

public enum ScanOutcome {
    GRANTED,
    DUPLICATE,
    DENIED,
    EXPIRED,
    INVALID_TOKEN,
    GATE_UNAVAILABLE
}
