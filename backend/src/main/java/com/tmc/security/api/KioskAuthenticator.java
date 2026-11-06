package com.tmc.security.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Port implemented by the gate module so the security filter does not depend on
 * gate internals (DIP).
 */
public interface KioskAuthenticator {

    Optional<KioskCredentials> authenticate(String kioskId);

    record KioskCredentials(UUID kioskId, String secret, boolean active) {
    }
}
