package com.clinic.schedule.cashdesk.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.cashdesk.domain.Visit;

/**
 * Where the visits, their charges and their refunds are kept (D-87, D-88). Nothing kept is changed nor
 * deleted: saving a visit adds it, and adds the charges and refunds it did not have.
 */
public interface VisitRepository {

    /**
     * Adds the visit, and the charges and refunds it did not have kept.
     *
     * @throws com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException when someone else
     *         charged the visit, or gave back from it, meanwhile
     */
    void save(Visit visit);

    Optional<Visit> findById(UUID id);

    /**
     * The visits, whole, with a charge or a refund recorded from {@code from}, included, to {@code until},
     * excluded.
     */
    List<Visit> findWithMovementsBetween(LocalDateTime from, LocalDateTime until);

    /** The visits not charged yet that started from {@code from}, included, to {@code until}, excluded. */
    List<Visit> findPendingArrivedBetween(LocalDateTime from, LocalDateTime until);
}
