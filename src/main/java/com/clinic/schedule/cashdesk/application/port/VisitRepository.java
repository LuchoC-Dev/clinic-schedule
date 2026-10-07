package com.clinic.schedule.cashdesk.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.cashdesk.domain.Visit;

/**
 * Where the visits and their charges are kept (D-87). Nothing kept is changed nor deleted: saving a visit
 * adds it, or adds the charge it did not have.
 */
public interface VisitRepository {

    /**
     * Adds the visit, or its charge when the visit was kept without one.
     *
     * @throws com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException when someone else
     *         charged the visit meanwhile
     */
    void save(Visit visit);

    Optional<Visit> findById(UUID id);

    /** The visits whose charge was recorded from {@code from}, included, to {@code until}, excluded. */
    List<Visit> findChargedBetween(LocalDateTime from, LocalDateTime until);

    /** The visits not charged yet that started from {@code from}, included, to {@code until}, excluded. */
    List<Visit> findPendingArrivedBetween(LocalDateTime from, LocalDateTime until);
}
