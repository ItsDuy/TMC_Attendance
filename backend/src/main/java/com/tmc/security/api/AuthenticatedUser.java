package com.tmc.security.api;

import java.util.UUID;

public record AuthenticatedUser(String username, Role role, UUID employeeId) {
}
