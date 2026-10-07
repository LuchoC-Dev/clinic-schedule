package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data over {@link VisitRow}; the application talks to {@code VisitRepository}, the port. */
interface VisitRowRepository extends JpaRepository<VisitRow, UUID> {

    @Query("select v from VisitRow v where v.arrivedAt >= :from and v.arrivedAt < :until"
            + " and not exists (select c from ChargeRow c where c.visitId = v.id)")
    List<VisitRow> findPendingArrivedBetween(LocalDateTime from, LocalDateTime until);
}
