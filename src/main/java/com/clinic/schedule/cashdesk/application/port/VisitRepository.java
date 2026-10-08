package com.clinic.schedule.cashdesk.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.cashdesk.domain.Visit;

/**
 * Where the visits, their charges and their refunds, and their coverage, are kept (D-87, D-88, D-90). Nothing
 * kept is changed nor deleted: saving a visit adds it, and adds the charges, refunds, coverage and withdrawal it
 * did not have.
 */
public interface VisitRepository {

    /**
     * Adds the visit, and the charges, refunds, coverage and withdrawal it did not have kept.
     *
     * @throws com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException when someone else
     *         charged the visit, gave back from it, covered it or withdrew its coverage meanwhile
     */
    void save(Visit visit);

    Optional<Visit> findById(UUID id);

    /**
     * The visit, held until the transaction ends, so nobody else charges it, covers it or withdraws its coverage
     * meanwhile (D-90): those write different rows, which no unique key holds together.
     */
    Optional<Visit> findByIdForUpdate(UUID id);

    /**
     * The visits, whole, with a charge or a refund recorded from {@code from}, included, to {@code until},
     * excluded.
     */
    List<Visit> findWithMovementsBetween(LocalDateTime from, LocalDateTime until);

    /**
     * The visits neither charged nor covered yet that started from {@code from}, included, to {@code until},
     * excluded.
     */
    List<Visit> findPendingArrivedBetween(LocalDateTime from, LocalDateTime until);

    /**
     * The visits, whole, whose coverage was recorded from {@code from}, included, to {@code until}, excluded,
     * withdrawn or not.
     */
    List<Visit> findCoveredBetween(LocalDateTime from, LocalDateTime until);
}
