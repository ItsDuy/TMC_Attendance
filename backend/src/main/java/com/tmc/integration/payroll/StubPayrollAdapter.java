package com.tmc.integration.payroll;

import com.tmc.reporting.api.PayrollAck;
import com.tmc.reporting.api.PayrollBatch;
import com.tmc.reporting.api.PayrollPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stand-in for the customer's HR/Payroll API. Swap for an HTTP adapter that
 * posts the same {@link PayrollBatch} with the idempotency key in a header.
 */
@Component
public class StubPayrollAdapter implements PayrollPort {

    private static final Logger log = LoggerFactory.getLogger(StubPayrollAdapter.class);

    @Override
    public PayrollAck send(PayrollBatch batch) {
        String reference = "HR-" + UUID.randomUUID();
        log.info("Delivered {} rows for {} to HR/Payroll (ref {}, checksum {})",
                batch.recordCount(), batch.period(), reference,
                batch.checksum().substring(0, 12));
        return new PayrollAck(true, reference, "Accepted " + batch.recordCount() + " rows");
    }
}
