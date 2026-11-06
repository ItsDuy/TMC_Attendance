package com.tmc.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            select e from OutboxEvent e
            where e.status = com.tmc.shared.outbox.OutboxStatus.PENDING
              and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
            order by e.createdAt asc
            """)
    List<OutboxEvent> findDue(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);
}
