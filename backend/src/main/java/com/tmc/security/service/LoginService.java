package com.tmc.security.service;

import com.tmc.security.api.AuditPort;
import com.tmc.security.api.AuthenticatedUser;
import com.tmc.security.domain.AppUser;
import com.tmc.security.jwt.JwtService;
import com.tmc.security.repo.AppUserRepository;
import com.tmc.shared.error.DomainException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Owns credential verification and token issuance so the web layer stays a thin
 * adapter (SRP).
 */
@Service
public class LoginService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditPort audit;

    public LoginService(AppUserRepository users, PasswordEncoder passwordEncoder,
                        JwtService jwtService, AuditPort audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    @Transactional(noRollbackFor = DomainException.class)
    public LoginResult login(String username, String password) {
        AppUser user = users.findByUsername(username)
                .filter(AppUser::isEnabled)
                .orElse(null);

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            audit.record(username, "LOGIN_FAILED", "AppUser", username,
                    Map.of("reason", "bad-credentials"));
            throw DomainException.unauthorized("Invalid username or password");
        }

        AuthenticatedUser principal = new AuthenticatedUser(
                user.getUsername(), user.getRole(), user.getEmployeeId());
        String token = jwtService.issue(principal);
        audit.record(user.getUsername(), "LOGIN_SUCCEEDED", "AppUser", user.getId().toString(),
                Map.of("role", user.getRole().name()));

        return new LoginResult(token, principal, jwtService.ttlSeconds());
    }

    public record LoginResult(String token, AuthenticatedUser user, long expiresInSeconds) {
    }
}
