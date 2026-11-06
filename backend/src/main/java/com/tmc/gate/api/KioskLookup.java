package com.tmc.gate.api;

import java.util.Optional;
import java.util.UUID;

public interface KioskLookup {

    Optional<KioskView> find(UUID kioskId);
}
