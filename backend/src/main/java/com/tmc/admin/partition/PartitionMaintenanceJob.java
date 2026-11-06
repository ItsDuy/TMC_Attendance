package com.tmc.admin.partition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Keeps next month's attendance partition ahead of the write path (ADR-013).
 */
@Component
public class PartitionMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceJob.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PartitionMaintenanceJob(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "${tmc.partition.cron:0 5 0 * * *}")
    public void provisionAhead() {
        YearMonth next = YearMonth.now(clock).plusMonths(1);
        try {
            jdbc.update("select ensure_attendance_partition(?)",
                    Date.valueOf(LocalDate.of(next.getYear(), next.getMonthValue(), 1)));
        } catch (RuntimeException e) {
            log.error("Failed to provision attendance partition for {}", next, e);
        }
    }
}
