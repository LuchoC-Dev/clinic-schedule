package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link CoverageWithdrawalRow}; the application talks to {@code VisitRepository}, the port. */
interface CoverageWithdrawalRowRepository extends JpaRepository<CoverageWithdrawalRow, UUID> {
}
