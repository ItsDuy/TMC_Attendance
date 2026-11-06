package com.tmc.identity.repo;

import com.tmc.identity.domain.Factory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FactoryRepository extends JpaRepository<Factory, UUID> {

    Optional<Factory> findByCode(String code);
}
