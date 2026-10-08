package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

/** Spring Data over {@link VisitRow}; the application talks to {@code VisitRepository}, the port. */
interface VisitRowRepository extends JpaRepository<VisitRow, UUID> {

    @Query("select v from VisitRow v where v.arrivedAt >= :from and v.arrivedAt < :until"
            + " and not exists (select c from ChargeRow c where c.visitId = v.id)"
            + " and not exists (select k from CoverageRow k where k.visitId = v.id)")
    List<VisitRow> findPendingArrivedBetween(LocalDateTime from, LocalDateTime until);

    /** The visit, its row locked until the transaction ends (D-90). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VisitRow v where v.id = :id")
    Optional<VisitRow> findForUpdate(UUID id);
}
