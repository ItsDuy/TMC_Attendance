package com.tmc.reporting.api;

public record PayrollAck(boolean accepted, String reference, String detail) {
}
