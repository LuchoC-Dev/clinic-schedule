package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link ChargeRow}; the application talks to {@code VisitRepository}, the port. */
interface ChargeRowRepository extends JpaRepository<ChargeRow, UUID> {

    Optional<ChargeRow> findByVisitId(UUID visitId);

    List<ChargeRow> findByRecordedAtGreaterThanEqualAndRecordedAtLessThan(LocalDateTime from, LocalDateTime until);
}
