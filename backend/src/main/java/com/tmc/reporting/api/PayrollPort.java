package com.tmc.reporting.api;

/**
 * Narrow outbound port for the HR/Payroll system (ISP + DIP). Implementations
 * live in the integration module.
 */
public interface PayrollPort {

    PayrollAck send(PayrollBatch batch);
}
