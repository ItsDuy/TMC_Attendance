package com.tmc.gate.repo;

import com.tmc.gate.domain.Kiosk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KioskRepository extends JpaRepository<Kiosk, UUID> {

    List<Kiosk> findByFactoryIdOrderByNameAsc(UUID factoryId);
}
