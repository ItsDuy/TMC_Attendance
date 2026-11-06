package com.tmc.identity.api;

public class InvalidQrTokenException extends RuntimeException {

    public enum Reason {
        MALFORMED,
        BAD_SIGNATURE,
        EXPIRED,
        NOT_YET_VALID
    }

    private final Reason reason;

    public InvalidQrTokenException(Reason reason) {
        super(switch (reason) {
            case MALFORMED -> "Malformed QR token";
            case BAD_SIGNATURE -> "QR token signature is invalid";
            case EXPIRED -> "QR token has expired";
            case NOT_YET_VALID -> "QR token was presented before it was issued";
        });
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
