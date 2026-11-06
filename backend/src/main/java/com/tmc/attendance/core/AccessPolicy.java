package com.tmc.attendance.core;

import java.util.Optional;

/**
 * A single access rule. Policies compose: the orchestrator chains them and stops
 * at the first denial, so adding a rule never requires editing existing ones (OCP).
 */
public interface AccessPolicy {

    Optional<Denial> evaluate(ScanContext context);

    record Denial(String code, String message) {
    }
}
