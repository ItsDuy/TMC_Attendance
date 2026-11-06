package com.tmc.identity.web;

import com.tmc.identity.api.FactoryLookup;
import com.tmc.identity.api.FactoryView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/factories")
@PreAuthorize("isAuthenticated()")
public class FactoryController {

    private final FactoryLookup factories;

    public FactoryController(FactoryLookup factories) {
        this.factories = factories;
    }

    @GetMapping
    public List<FactoryView> list() {
        return factories.findAll();
    }
}
