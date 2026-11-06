package com.tmc.reporting.service;

import com.tmc.attendance.api.AttendanceReportingPort.SiteEvent;
import com.tmc.attendance.domain.Direction;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single rule for "who is inside": a person is inside when their latest COMPLETED scan is IN
 * (kiosk, offline sync or guard manual entry alike). The live map's head count and the evacuation
 * list both use this class, so the two can never disagree (Single Responsibility, no duplicated rule).
 */
final class Occupancy {

    private final Map<UUID, SiteEvent> latestCompleted;

    private Occupancy(Map<UUID, SiteEvent> latestCompleted) {
        this.latestCompleted = latestCompleted;
    }

    /** @param eventsNewestFirst scans as the attendance port returns them, newest first */
    static Occupancy from(List<SiteEvent> eventsNewestFirst) {
        Map<UUID, SiteEvent> latest = new LinkedHashMap<>();
        for (SiteEvent event : eventsNewestFirst) {
            if ("COMPLETED".equals(event.status())) {
                latest.putIfAbsent(event.employeeId(), event);
            }
        }
        return new Occupancy(latest);
    }

    /** The scan that put each person inside this factory, newest first. */
    List<SiteEvent> insideOf(UUID factoryId) {
        return latestCompleted.values().stream()
                .filter(event -> event.factoryId().equals(factoryId))
                .filter(event -> event.direction() == Direction.IN)
                .sorted(Comparator.comparing(SiteEvent::scannedAt).reversed())
                .toList();
    }
}
