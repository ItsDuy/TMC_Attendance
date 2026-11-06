package com.tmc.attendance.domain;

public enum EventSource {
    ONLINE,
    OFFLINE,
    /** Entered by a security guard because the phone, kiosk or network was down. */
    MANUAL
}
