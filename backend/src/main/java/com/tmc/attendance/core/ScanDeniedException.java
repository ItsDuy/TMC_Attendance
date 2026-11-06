package com.tmc.attendance.core;

public class ScanDeniedException extends RuntimeException {

    private final String code;
    private final ScanOutcome outcome;

    public ScanDeniedException(String code, ScanOutcome outcome, String message) {
        super(message);
        this.code = code;
        this.outcome = outcome;
    }

    public String code() {
        return code;
    }

    public ScanOutcome outcome() {
        return outcome;
    }
}
