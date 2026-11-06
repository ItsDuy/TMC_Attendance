package com.tmc.identity.repo;

import com.tmc.identity.domain.QrCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QrCredentialRepository extends JpaRepository<QrCredential, UUID> {
}
