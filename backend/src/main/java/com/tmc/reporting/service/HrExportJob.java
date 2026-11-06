package com.tmc.reporting.service;

import com.tmc.identity.api.FactoryLookup;
import com.tmc.identity.api.FactoryView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;

/**
 * Runs every 30 minutes from 06:00 to 11:30 on the first of each month and exports
 * the previous period for every factory. Batches that were already SENT are returned
 * untouched, so the extra runs only retry FAILED ones: the 12:00 reconciliation
 * deadline gets a twelve-attempt retry buffer (ADR-010).
 */
@Component
public class HrExportJob {

    private static final Logger log = LoggerFactory.getLogger(HrExportJob.class);

    private final HrExportService exporter;
    private final FactoryLookup factories;
    private final Clock clock;

    public HrExportJob(HrExportService exporter, FactoryLookup factories, Clock clock) {
        this.exporter = exporter;
        this.factories = factories;
        this.clock = clock;
    }

    @Scheduled(cron = "${tmc.report.export-cron:0 0/30 6-11 1 * *}")
    public void monthlyExport() {
        YearMonth period = YearMonth.now(clock).minusMonths(1);
        for (FactoryView factory : factories.findAll()) {
            try {
                var batch = exporter.export(period, factory.id(), "scheduler");
                log.info("HR export {} / {} -> {} ({} records)",
                        period, factory.code(), batch.getStatus(), batch.getRecordCount());
            } catch (Exception e) {
                log.error("HR export failed for {} / {}", period, factory.code(), e);
            }
        }
    }
}
