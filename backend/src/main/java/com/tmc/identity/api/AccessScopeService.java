package com.tmc.identity.api;

import com.tmc.security.api.AuthenticatedUser;
import org.springframework.stereotype.Service;

/**
 * The single place that decides what each role may see about other people (Single
 * Responsibility). Adding a role means one new case here; no endpoint changes.
 */
@Service
public class AccessScopeService {

    private final EmployeeLookup employees;

    public AccessScopeService(EmployeeLookup employees) {
        this.employees = employees;
    }

    public AccessScope scopeFor(AuthenticatedUser user) {
        return switch (user.role()) {
            case HR -> AccessScope.EVERYONE;
            case ADMIN, SECURITY -> AccessScope.EVERYONE_BY_NAME;
            case EXECUTIVE -> AccessScope.COUNTS_ONLY;
            case SUPERVISOR -> user.employeeId() == null
                    ? AccessScope.NOBODY
                    : employees.find(user.employeeId())
                            .map(me -> AccessScope.team(me.factoryId(), me.department()))
                            .orElse(AccessScope.NOBODY);
            default -> AccessScope.NOBODY;
        };
    }
}
