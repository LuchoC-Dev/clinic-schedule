package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.cashdesk.application.port.VisitRepository;
import com.clinic.schedule.cashdesk.domain.Charge;
import com.clinic.schedule.cashdesk.domain.Coverage;
import com.clinic.schedule.cashdesk.domain.CoverageWithdrawal;
import com.clinic.schedule.cashdesk.domain.Visit;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

/**
 * Implements the port over JPA, inserting and never updating (D-87, D-88, D-90): a new visit is added, and so is
 * each charge and refund it did not have kept, numbered by its place, and its coverage and the coverage's
 * withdrawal.
 */
@Repository
class VisitJpaAdapter implements VisitRepository {

    private final VisitRowRepository visits;
    private final ChargeRowRepository charges;
    private final RefundRowRepository refunds;
    private final CoverageRowRepository coverages;
    private final CoverageWithdrawalRowRepository withdrawals;

    VisitJpaAdapter(VisitRowRepository visits, ChargeRowRepository charges, RefundRowRepository refunds,
            CoverageRowRepository coverages, CoverageWithdrawalRowRepository withdrawals) {
        this.visits = visits;
        this.charges = charges;
        this.refunds = refunds;
        this.coverages = coverages;
        this.withdrawals = withdrawals;
    }

    /**
     * Every row is immutable: saving one already kept leaves it as it was. Each is flushed as it is saved, so
     * a charge or a refund another request added meanwhile, taking the same number, is refused here, and so is a
     * coverage or a withdrawal other than the one the visit has kept (D-90).
     */
    @Override
    public void save(Visit visit) {
        visits.save(VisitRow.from(visit));
        visit.coverage().ifPresent(coverage -> {
            CoverageRow coverageRow = CoverageRow.from(visit.id(), coverage);
            Optional<CoverageRow> kept = coverages.findById(visit.id());
            if (kept.isPresent() ? !kept.get().sameAs(coverageRow) : !inserted(coverages, coverageRow)) {
                throw new ChargeRuleViolationException(Reason.ALREADY_COVERED, "The visit was covered already");
            }
            if (coverage.withdrawal() != null) {
                CoverageWithdrawalRow withdrawalRow = CoverageWithdrawalRow.from(visit.id(), coverage.withdrawal());
                Optional<CoverageWithdrawalRow> withdrawn = withdrawals.findById(visit.id());
                if (withdrawn.isPresent() ? !withdrawn.get().sameAs(withdrawalRow)
                        : !inserted(withdrawals, withdrawalRow)) {
                    throw new ChargeRuleViolationException(Reason.NOT_COVERED, "The coverage was withdrawn already");
                }
            }
        });
        List<Charge> all = visit.charges();
        for (int c = 0; c < all.size(); c++) {
            Charge charge = all.get(c);
            try {
                charges.saveAndFlush(ChargeRow.from(visit.id(), c + 1, charge));
            } catch (DataIntegrityViolationException e) {
                throw new ChargeRuleViolationException(Reason.ALREADY_CHARGED, "The visit was charged already");
            }
            for (int r = 0; r < charge.refunds().size(); r++) {
                try {
                    refunds.saveAndFlush(RefundRow.from(charge.id(), r + 1, charge.refunds().get(r)));
                } catch (DataIntegrityViolationException e) {
                    throw new ChargeRuleViolationException(Reason.CHANGED_MEANWHILE,
                            "Someone else gave back from the charge meanwhile");
                }
            }
        }
    }

    /**
     * Adds a row keyed by its visit, false when another request added one meanwhile. A row kept already is
     * compared rather than saved again: being keyed by the visit, saving would leave the one kept as it was, and
     * say nothing of a different one.
     */
    private static <T> boolean inserted(JpaRepository<T, UUID> rows, T row) {
        try {
            rows.saveAndFlush(row);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Override
    public Optional<Visit> findById(UUID id) {
        return visits.findById(id).map(row -> whole(List.of(row)).getFirst());
    }

    @Override
    public Optional<Visit> findByIdForUpdate(UUID id) {
        return visits.findForUpdate(id).map(row -> whole(List.of(row)).getFirst());
    }

    @Override
    public List<Visit> findWithMovementsBetween(LocalDateTime from, LocalDateTime until) {
        Set<UUID> ids = new HashSet<>();
        charges.findByRecordedAtGreaterThanEqualAndRecordedAtLessThan(from, until)
                .forEach(charge -> ids.add(charge.visitId()));
        List<UUID> refundedCharges = refunds.findByRecordedAtGreaterThanEqualAndRecordedAtLessThan(from, until)
                .stream().map(RefundRow::chargeId).toList();
        charges.findAllById(refundedCharges).forEach(charge -> ids.add(charge.visitId()));
        return whole(visits.findAllById(ids));
    }

    @Override
    public List<Visit> findPendingArrivedBetween(LocalDateTime from, LocalDateTime until) {
        return visits.findPendingArrivedBetween(from, until).stream()
                .map(row -> row.toVisit(null, List.of()))
                .toList();
    }

    @Override
    public List<Visit> findCoveredBetween(LocalDateTime from, LocalDateTime until) {
        return whole(visits.findAllById(coverages.findByRecordedAtGreaterThanEqualAndRecordedAtLessThan(from, until)
                .stream().map(CoverageRow::visitId).toList()));
    }

    /** The visits with their coverage, and their charges and refunds, each in the order they were recorded. */
    private List<Visit> whole(Collection<VisitRow> rows) {
        List<UUID> ids = rows.stream().map(VisitRow::id).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ChargeRow> chargeRows = charges.findByVisitIdIn(ids);
        Map<UUID, List<RefundRow>> refundsByCharge = chargeRows.isEmpty() ? Map.of()
                : refunds.findByChargeIdIn(chargeRows.stream().map(ChargeRow::id).toList()).stream()
                        .collect(Collectors.groupingBy(RefundRow::chargeId));
        Map<UUID, List<Charge>> chargesByVisit = chargeRows.stream()
                .sorted(Comparator.comparingInt(ChargeRow::number))
                .collect(Collectors.groupingBy(ChargeRow::visitId, Collectors.mapping(
                        row -> row.toCharge(refundsByCharge.getOrDefault(row.id(), List.of()).stream()
                                .sorted(Comparator.comparingInt(RefundRow::number))
                                .map(RefundRow::toRefund)
                                .toList()),
                        Collectors.toList())));
        Map<UUID, CoverageWithdrawal> withdrawalByVisit = withdrawals.findAllById(ids).stream()
                .collect(Collectors.toMap(CoverageWithdrawalRow::visitId, CoverageWithdrawalRow::toWithdrawal));
        Map<UUID, Coverage> coverageByVisit = coverages.findAllById(ids).stream()
                .collect(Collectors.toMap(CoverageRow::visitId,
                        row -> row.toCoverage(withdrawalByVisit.get(row.visitId()))));
        return rows.stream()
                .map(row -> row.toVisit(coverageByVisit.get(row.id()),
                        chargesByVisit.getOrDefault(row.id(), List.of())))
                .toList();
    }
}
