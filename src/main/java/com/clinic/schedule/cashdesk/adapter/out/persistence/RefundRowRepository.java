package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link RefundRow}; the application talks to {@code VisitRepository}, the port. */
interface RefundRowRepository extends JpaRepository<RefundRow, UUID> {

    List<RefundRow> findByChargeIdIn(Collection<UUID> chargeIds);

    List<RefundRow> findByRecordedAtGreaterThanEqualAndRecordedAtLessThan(LocalDateTime from, LocalDateTime until);
}
