package com.tmc.attendance.core;

import com.tmc.attendance.domain.Direction;
import com.tmc.gate.domain.GateDirection;

import java.util.Optional;

/**
 * Gate-side direction rule. The three implementations are interchangeable
 * (Liskov): the orchestrator only sees this interface.
 */
public interface DirectionPolicy extends AccessPolicy {

    GateDirection gateType();

    static DirectionPolicy forGate(GateDirection gateDirection) {
        return switch (gateDirection) {
            case IN -> new EntryDirectionPolicy();
            case OUT -> new ExitDirectionPolicy();
            case BIDIRECTIONAL -> new BidirectionalPolicy();
        };
    }

    record EntryDirectionPolicy() implements DirectionPolicy {
        @Override
        public GateDirection gateType() {
            return GateDirection.IN;
        }

        @Override
        public Optional<Denial> evaluate(ScanContext context) {
            return context.direction() == Direction.IN
                    ? Optional.empty()
                    : Optional.of(new Denial("WRONG_DIRECTION", "This gate only admits entry"));
        }
    }

    record ExitDirectionPolicy() implements DirectionPolicy {
        @Override
        public GateDirection gateType() {
            return GateDirection.OUT;
        }

        @Override
        public Optional<Denial> evaluate(ScanContext context) {
            return context.direction() == Direction.OUT
                    ? Optional.empty()
                    : Optional.of(new Denial("WRONG_DIRECTION", "This gate only admits exit"));
        }
    }

    record BidirectionalPolicy() implements DirectionPolicy {
        @Override
        public GateDirection gateType() {
            return GateDirection.BIDIRECTIONAL;
        }

        @Override
        public Optional<Denial> evaluate(ScanContext context) {
            return Optional.empty();
        }
    }
}
