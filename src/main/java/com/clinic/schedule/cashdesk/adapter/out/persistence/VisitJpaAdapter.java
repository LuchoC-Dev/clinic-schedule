package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.cashdesk.application.port.VisitRepository;
import com.clinic.schedule.cashdesk.domain.Visit;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

/**
 * Implements the port over JPA, inserting and never updating (D-87): a new visit is added, and a charge is
 * added to a visit kept without one.
 */
@Repository
class VisitJpaAdapter implements VisitRepository {

    private final VisitRowRepository visits;
    private final ChargeRowRepository charges;

    VisitJpaAdapter(VisitRowRepository visits, ChargeRowRepository charges) {
        this.visits = visits;
        this.charges = charges;
    }

    /** Both rows are immutable: saving one already kept leaves it as it was. */
    @Override
    public void save(Visit visit) {
        visits.save(VisitRow.from(visit));
        if (visit.charge().isPresent()) {
            try {
                // Flushed here, so a charge another request added meanwhile is refused as one, not at commit.
                charges.saveAndFlush(ChargeRow.from(visit.id(), visit.charge().get()));
            } catch (DataIntegrityViolationException e) {
                throw new ChargeRuleViolationException(Reason.ALREADY_CHARGED, "The visit was charged already");
            }
        }
    }

    @Override
    public Optional<Visit> findById(UUID id) {
        return visits.findById(id)
                .map(row -> row.toVisit(charges.findByVisitId(id).map(ChargeRow::toCharge).orElse(null)));
    }

    @Override
    public List<Visit> findChargedBetween(LocalDateTime from, LocalDateTime until) {
        Map<UUID, ChargeRow> byVisit = charges.findByRecordedAtGreaterThanEqualAndRecordedAtLessThan(from, until)
                .stream()
                .collect(Collectors.toMap(ChargeRow::visitId, Function.identity()));
        return visits.findAllById(byVisit.keySet()).stream()
                .map(row -> row.toVisit(byVisit.get(row.id()).toCharge()))
                .toList();
    }

    @Override
    public List<Visit> findPendingArrivedBetween(LocalDateTime from, LocalDateTime until) {
        return visits.findPendingArrivedBetween(from, until).stream().map(row -> row.toVisit(null)).toList();
    }
}
