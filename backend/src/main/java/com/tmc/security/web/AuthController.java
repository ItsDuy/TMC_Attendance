package com.tmc.security.web;

import com.tmc.security.service.LoginService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        LoginService.LoginResult result = loginService.login(request.username(), request.password());
        return new LoginResponse(
                result.token(),
                result.user().username(),
                result.user().role().name(),
                result.user().employeeId(),
                result.expiresInSeconds());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, String username, String role, UUID employeeId,
                                long expiresInSeconds) {
    }
}
