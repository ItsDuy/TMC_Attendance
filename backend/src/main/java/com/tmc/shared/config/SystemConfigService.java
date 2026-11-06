package com.tmc.shared.config;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SystemConfigService {

    private final SystemConfigRepository repository;

    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    public int getInt(String key, int fallback) {
        return repository.findById(key)
                .map(config -> Integer.parseInt(config.getValue()))
                .orElse(fallback);
    }

    public String get(String key, String fallback) {
        return repository.findById(key).map(SystemConfig::getValue).orElse(fallback);
    }

    public List<SystemConfig> all() {
        return repository.findAll();
    }

    @Transactional
    public SystemConfig update(String key, String value) {
        SystemConfig config = repository.findById(key)
                .orElseThrow(() -> com.tmc.shared.error.DomainException.notFound("Config key", key));
        config.setValue(value);
        return repository.save(config);
    }
}
