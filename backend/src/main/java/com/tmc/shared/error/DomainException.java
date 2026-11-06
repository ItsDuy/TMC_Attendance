package com.tmc.shared.error;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public DomainException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public static DomainException notFound(String what, Object id) {
        return new DomainException("NOT_FOUND", HttpStatus.NOT_FOUND, what + " not found: " + id);
    }

    public static DomainException conflict(String code, String message) {
        return new DomainException(code, HttpStatus.CONFLICT, message);
    }

    public static DomainException badRequest(String code, String message) {
        return new DomainException(code, HttpStatus.BAD_REQUEST, message);
    }

    public static DomainException unauthorized(String message) {
        return new DomainException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, message);
    }
}
