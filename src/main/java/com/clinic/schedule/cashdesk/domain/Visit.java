package com.clinic.schedule.cashdesk.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

/**
 * Each time reception puts a patient into a queue, and what was charged and given back for it (D-87, D-88).
 * Its id is the queue's entry, so giving the patient to another practitioner, or calling them again, is the
 * same visit.
 *
 * <p>A visit that is not an emergency is charged as it starts, or covered by the patient's health insurance, or
 * it does not start. An emergency may be charged or covered then, or is left pending and charged or covered
 * later, or never. A covered visit is charged only its copay, if the insurer asks for one (D-90). What went wrong
 * is given back, some or all of it; a visit given back whole may be charged again. So a visit has its charges in
 * the order they were recorded, and only the last may have something left of it: that is the current one. A
 * coverage marked by mistake is withdrawn, once nothing is left of its copay, and the visit is then charged
 * privately. Immutable: charging, giving back, covering and withdrawing answer a new visit (D-17).
 */
public final class Visit {

    private final UUID id;
    private final UUID patientId;
    private final LocalDateTime arrivedAt;
    private final boolean emergency;
    private final Coverage coverage;
    private final List<Charge> charges;

    private Visit(UUID id, UUID patientId, LocalDateTime arrivedAt, boolean emergency, Coverage coverage,
            List<Charge> charges) {
        if (id == null || patientId == null || arrivedAt == null || charges == null) {
            throw new IllegalArgumentException("A visit needs its id, its patient and when it started");
        }
        if (!emergency && charges.isEmpty() && coverage == null) {
            throw new ChargeRuleViolationException(Reason.CHARGE_REQUIRED,
                    "A visit that is not an emergency is charged, or covered, when the patient comes in");
        }
        if (charges.stream().limit(Math.max(charges.size() - 1, 0)).anyMatch(charge -> !charge.settled())) {
            throw new IllegalArgumentException("A visit is charged again only once what it was charged is given back");
        }
        this.id = id;
        this.patientId = patientId;
        this.arrivedAt = arrivedAt;
        this.emergency = emergency;
        this.coverage = coverage;
        this.charges = List.copyOf(charges);
    }

    /**
     * The patient came into a queue.
     *
     * @param coverage the patient's insurance said yes (D-90); empty when they pay privately
     * @param amount what reception charged — the copay, for a covered visit —; empty only for an emergency, whose
     *        charge is left pending, or a covered visit with no copay
     * @param means how it was paid; unused when nothing was charged
     * @param by the username of whoever brought them in
     */
    public static Visit arrive(UUID id, UUID patientId, boolean emergency, Optional<Coverage> coverage,
            Optional<Amount> amount, Means means, String by, LocalDateTime at) {
        return new Visit(id, patientId, at, emergency, coverage.orElse(null), amount
                .map(paid -> List.of(Charge.of(paid, means, coverage.isPresent(), by, at)))
                .orElse(List.of()));
    }

    /**
     * A visit as it was recorded, read back, its charges in the order they were.
     *
     * @param coverage null when it was never covered
     */
    public static Visit recorded(UUID id, UUID patientId, LocalDateTime arrivedAt, boolean emergency,
            Coverage coverage, List<Charge> charges) {
        return new Visit(id, patientId, arrivedAt, emergency, coverage, charges);
    }

    /**
     * An emergency's pending charge, or a visit given back whole charged again. On a covered visit, it is its
     * copay.
     */
    public Visit charge(Amount amount, Means means, String by, LocalDateTime at) {
        if (current().isPresent()) {
            throw new ChargeRuleViolationException(Reason.ALREADY_CHARGED, "The visit was charged already");
        }
        List<Charge> after = new ArrayList<>(charges);
        after.add(Charge.of(amount, means, covered(), by, at));
        return new Visit(id, patientId, arrivedAt, emergency, coverage, after);
    }

    /**
     * An emergency's pending visit, recorded later as covered (D-90), with its copay if the insurer asks for one.
     *
     * @param copay empty when the insurer asks for none
     * @param means how the copay was paid; unused without one
     */
    public Visit cover(Coverage covered, Optional<Amount> copay, Means means, String by, LocalDateTime at) {
        if (coverage != null) {
            throw new ChargeRuleViolationException(Reason.ALREADY_COVERED, "The visit was covered already");
        }
        if (!charges.isEmpty()) {
            throw new ChargeRuleViolationException(Reason.ALREADY_CHARGED, "The visit was charged privately already");
        }
        return new Visit(id, patientId, arrivedAt, emergency, covered,
                copay.map(paid -> List.of(Charge.of(paid, means, true, by, at))).orElse(List.of()));
    }

    /**
     * A coverage marked by mistake, withdrawn as a fact of its own (D-90), once nothing is left of its copay: the
     * visit is then charged privately, as one given back whole.
     */
    public Visit withdrawCoverage(String reason, String by, LocalDateTime at) {
        if (!covered()) {
            throw new ChargeRuleViolationException(Reason.NOT_COVERED, "The visit has no coverage to withdraw");
        }
        if (current().isPresent()) {
            throw new ChargeRuleViolationException(Reason.COPAY_LEFT,
                    "The copay is given back whole before the coverage is withdrawn");
        }
        return new Visit(id, patientId, arrivedAt, emergency, coverage.withdraw(reason, by, at), charges);
    }

    /** Some or all of what is left of the current charge, given back. */
    public Visit refund(Amount amount, String reason, String by, LocalDateTime at) {
        Charge charge = current().orElseThrow(() -> new ChargeRuleViolationException(Reason.NOTHING_TO_REFUND,
                "There is nothing left to give back"));
        List<Charge> after = new ArrayList<>(charges);
        after.set(after.size() - 1, charge.refund(amount, reason, by, at));
        return new Visit(id, patientId, arrivedAt, emergency, coverage, after);
    }

    public UUID id() {
        return id;
    }

    public UUID patientId() {
        return patientId;
    }

    public LocalDateTime arrivedAt() {
        return arrivedAt;
    }

    public boolean emergency() {
        return emergency;
    }

    /** Its coverage, standing or withdrawn; empty when it was never covered. */
    public Optional<Coverage> coverage() {
        return Optional.ofNullable(coverage);
    }

    /** Covered by the patient's insurance, and not withdrawn. */
    public boolean covered() {
        return coverage != null && coverage.standing();
    }

    /** Every charge, in the order they were recorded. */
    public List<Charge> charges() {
        return charges;
    }

    /** The charge with something left of it, if any: the last one. */
    public Optional<Charge> current() {
        return charges.isEmpty() || charges.getLast().settled() ? Optional.empty() : Optional.of(charges.getLast());
    }

    /** Never charged nor covered yet: an emergency's, only. */
    public boolean pending() {
        return charges.isEmpty() && coverage == null;
    }
}
