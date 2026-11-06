package com.tmc.reporting.repo;

import com.tmc.reporting.domain.HrExportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrExportBatchRepository extends JpaRepository<HrExportBatch, UUID> {

    Optional<HrExportBatch> findByIdempotencyKey(String idempotencyKey);

    List<HrExportBatch> findByPeriodOrderByCreatedAtDesc(String period);
}
