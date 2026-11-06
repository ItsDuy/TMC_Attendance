package com.tmc.identity.api;

import java.util.UUID;

/**
 * What a signed-in person may see about other people (least privilege), decided in one place:
 * {@link AccessScopeService}. Endpoints ask the scope; they never branch on roles themselves.
 *
 * @param factoryId / department  which employees: null = no limit (a supervisor: own factory + team)
 * @param nobody                  sees no individual at all
 * @param namesVisible            false = head counts only (executive)
 * @param contactDetailsVisible   email and phone (HR only)
 */
public record AccessScope(UUID factoryId, String department, boolean nobody,
                          boolean namesVisible, boolean contactDetailsVisible) {

    /** HR: everyone, with contact details. */
    public static final AccessScope EVERYONE = new AccessScope(null, null, false, true, true);
    /** Admin (IT), guard: everyone by name, no contact details. */
    public static final AccessScope EVERYONE_BY_NAME = new AccessScope(null, null, false, true, false);
    /** Executive management: numbers only. */
    public static final AccessScope COUNTS_ONLY = new AccessScope(null, null, false, false, false);
    public static final AccessScope NOBODY = new AccessScope(null, null, true, false, false);

    /** A production supervisor: their own team, by name. */
    public static AccessScope team(UUID factoryId, String department) {
        return new AccessScope(factoryId, department, false, true, false);
    }

    /** No limit on which employees (names may still be hidden). */
    public boolean isEveryone() {
        return !nobody && factoryId == null && department == null;
    }

    public boolean allows(EmployeeView employee) {
        if (nobody || employee == null) {
            return false;
        }
        return (factoryId == null || factoryId.equals(employee.factoryId()))
                && (department == null || department.equals(employee.department()));
    }
}
