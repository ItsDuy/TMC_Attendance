package com.tmc.attendance.core;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The one place that runs the access rules (Single Responsibility). Every {@link AccessPolicy}
 * bean is picked up automatically in {@code @Order}, and the gate's direction rule is added last,
 * so a new rule is a new class, never an edit here or in the callers (Open/Closed).
 * Used by both the kiosk scan and the guard's manual entry.
 */
@Component
public class AccessPolicyChain {

    private final List<AccessPolicy> policies;

    public AccessPolicyChain(List<AccessPolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    /** The first rule that says no, or empty when every rule allows the passage. */
    public Optional<AccessPolicy.Denial> firstDenial(ScanContext context) {
        return Stream.concat(policies.stream(),
                        Stream.of(DirectionPolicy.forGate(context.point().gateDirection())))
                .map(policy -> policy.evaluate(context))
                .flatMap(Optional::stream)
                .findFirst();
    }
}
