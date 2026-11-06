package com.tmc.security.api;

/**
 * Narrow audit contract (ISP). Anything that must be provably recorded goes
 * through this port, never through the audit repository.
 */
public interface AuditPort {

    void record(String actor, String action, String resourceType, String resourceId, Object payload);
}
