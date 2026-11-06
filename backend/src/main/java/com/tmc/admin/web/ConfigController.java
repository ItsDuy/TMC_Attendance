package com.tmc.admin.web;

import com.tmc.security.api.SecurityUtils;
import com.tmc.shared.config.SystemConfig;
import com.tmc.shared.config.SystemConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/config")
@PreAuthorize("hasRole('ADMIN')")
public class ConfigController {

    private final SystemConfigService config;
    private final com.tmc.security.api.AuditPort audit;

    public ConfigController(SystemConfigService config,
                            com.tmc.security.api.AuditPort audit) {
        this.config = config;
        this.audit = audit;
    }

    @GetMapping
    public List<ConfigResponse> all() {
        return config.all().stream().map(ConfigResponse::from).toList();
    }

    @PatchMapping("/{key}")
    public ConfigResponse update(@PathVariable String key,
                                 @Valid @RequestBody UpdateConfigRequest request,
                                 Authentication authentication) {
        SystemConfig updated = config.update(key, request.value());
        audit.record(SecurityUtils.actor(authentication), "CONFIG_UPDATED", "SystemConfig", key,
                java.util.Map.of("value", request.value()));
        return ConfigResponse.from(updated);
    }

    public record UpdateConfigRequest(@NotBlank String value) {
    }

    public record ConfigResponse(String key, String value, String description, Instant updatedAt) {
        static ConfigResponse from(SystemConfig config) {
            return new ConfigResponse(config.getKey(), config.getValue(), config.getDescription(),
                    config.getUpdatedAt());
        }
    }
}
