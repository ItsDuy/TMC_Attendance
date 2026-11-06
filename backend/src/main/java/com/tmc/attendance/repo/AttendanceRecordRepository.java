package com.tmc.attendance.repo;

import com.tmc.attendance.domain.AttendanceRecord;
import com.tmc.attendance.domain.AttendanceStatus;
import com.tmc.attendance.domain.EventSource;
import com.tmc.attendance.domain.Direction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findTopByEmployeeIdAndStatusInOrderByScannedAtDesc(
            UUID employeeId, Collection<AttendanceStatus> statuses);

    List<AttendanceRecord> findByStatusOrderByScannedAtAsc(AttendanceStatus status);

    List<AttendanceRecord> findBySourceAndScannedAtGreaterThanEqualAndScannedAtLessThanOrderByScannedAtDesc(
            EventSource source, Instant from, Instant to);

    @Query("""
            select r from AttendanceRecord r
            where r.scannedAt >= :from and r.scannedAt < :to
              and (:employeeId is null or r.employeeId = :employeeId)
              and (:factoryId is null or r.factoryId = :factoryId)
            order by r.scannedAt desc
            """)
    List<AttendanceRecord> search(@Param("from") Instant from,
                                  @Param("to") Instant to,
                                  @Param("employeeId") UUID employeeId,
                                  @Param("factoryId") UUID factoryId);

    @Query("""
            select r.employeeId as employeeId, r.direction as direction,
                   count(r) as eventCount, min(r.scannedAt) as firstAt, max(r.scannedAt) as lastAt
            from AttendanceRecord r
            where r.scannedAt >= :from and r.scannedAt < :to
              and r.status = :status
              and (:factoryId is null or r.factoryId = :factoryId)
            group by r.employeeId, r.direction
            """)
    List<MonthlyAggregate> aggregateByEmployee(@Param("from") Instant from,
                                               @Param("to") Instant to,
                                               @Param("status") AttendanceStatus status,
                                               @Param("factoryId") UUID factoryId);

    @Query("""
            select r.employeeId as employeeId,
                   count(distinct function('date', r.scannedAt)) as daysPresent
            from AttendanceRecord r
            where r.scannedAt >= :from and r.scannedAt < :to
              and r.status = :status
              and (:factoryId is null or r.factoryId = :factoryId)
            group by r.employeeId
            """)
    List<EmployeeDays> countDistinctDays(@Param("from") Instant from,
                                         @Param("to") Instant to,
                                         @Param("status") AttendanceStatus status,
                                         @Param("factoryId") UUID factoryId);

    interface MonthlyAggregate {
        UUID getEmployeeId();

        Direction getDirection();

        long getEventCount();

        Instant getFirstAt();

        Instant getLastAt();
    }

    interface EmployeeDays {
        UUID getEmployeeId();

        long getDaysPresent();
    }
}
