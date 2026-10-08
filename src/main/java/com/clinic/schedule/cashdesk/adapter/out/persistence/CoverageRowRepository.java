package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link CoverageRow}; the application talks to {@code VisitRepository}, the port. */
interface CoverageRowRepository extends JpaRepository<CoverageRow, UUID> {

    List<CoverageRow> findByRecordedAtGreaterThanEqualAndRecordedAtLessThan(LocalDateTime from, LocalDateTime until);
}
