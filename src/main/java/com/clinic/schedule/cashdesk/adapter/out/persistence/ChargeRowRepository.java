package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link ChargeRow}; the application talks to {@code VisitRepository}, the port. */
interface ChargeRowRepository extends JpaRepository<ChargeRow, UUID> {

    List<ChargeRow> findByVisitIdIn(Collection<UUID> visitIds);

    List<ChargeRow> findByRecordedAtGreaterThanEqualAndRecordedAtLessThan(LocalDateTime from, LocalDateTime until);
}
