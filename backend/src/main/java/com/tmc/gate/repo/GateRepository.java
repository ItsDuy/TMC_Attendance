package com.tmc.gate.repo;

import com.tmc.gate.domain.Gate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GateRepository extends JpaRepository<Gate, UUID> {

    List<Gate> findByFactoryIdOrderByNameAsc(UUID factoryId);

    boolean existsByFactoryIdAndName(UUID factoryId, String name);
}
