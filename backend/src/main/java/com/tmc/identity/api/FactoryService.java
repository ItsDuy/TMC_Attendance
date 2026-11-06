package com.tmc.identity.api;

import com.tmc.identity.repo.FactoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FactoryService implements FactoryLookup {

    private final FactoryRepository factories;

    public FactoryService(FactoryRepository factories) {
        this.factories = factories;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FactoryView> findAll() {
        return factories.findAll().stream()
                .map(factory -> new FactoryView(factory.getId(), factory.getCode(), factory.getName()))
                .toList();
    }
}
