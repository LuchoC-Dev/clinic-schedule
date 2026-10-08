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
 * <p>A visit that is not an emergency is charged as it starts, or it does not start. An emergency may be
 * charged then, or is left pending and charged later, or never. What went wrong is given back, some or all of
 * it; a visit given back whole may be charged again. So a visit has its charges in the order they were
 * recorded, and only the last may have something left of it: that is the current one. Immutable: charging
 * and giving back answer a new visit (D-17).
 */
public final class Visit {

    private final UUID id;
    private final UUID patientId;
    private final LocalDateTime arrivedAt;
    private final boolean emergency;
    private final List<Charge> charges;

    private Visit(UUID id, UUID patientId, LocalDateTime arrivedAt, boolean emergency, List<Charge> charges) {
        if (id == null || patientId == null || arrivedAt == null || charges == null) {
            throw new IllegalArgumentException("A visit needs its id, its patient and when it started");
        }
        if (!emergency && charges.isEmpty()) {
            throw new ChargeRuleViolationException(Reason.CHARGE_REQUIRED,
                    "A visit that is not an emergency is charged when the patient comes in");
        }
        if (charges.stream().limit(Math.max(charges.size() - 1, 0)).anyMatch(charge -> !charge.settled())) {
            throw new IllegalArgumentException("A visit is charged again only once what it was charged is given back");
        }
        this.id = id;
        this.patientId = patientId;
        this.arrivedAt = arrivedAt;
        this.emergency = emergency;
        this.charges = List.copyOf(charges);
    }

    /**
     * The patient came into a queue.
     *
     * @param amount what reception charged; empty only for an emergency, whose charge is left pending
     * @param means how it was paid; unused when nothing was charged
     * @param by the username of whoever brought them in
     */
    public static Visit arrive(UUID id, UUID patientId, boolean emergency, Optional<Amount> amount, Means means,
            String by, LocalDateTime at) {
        return new Visit(id, patientId, at, emergency,
                amount.map(paid -> List.of(Charge.of(paid, means, by, at))).orElse(List.of()));
    }

    /** A visit as it was recorded, read back, its charges in the order they were. */
    public static Visit recorded(UUID id, UUID patientId, LocalDateTime arrivedAt, boolean emergency,
            List<Charge> charges) {
        return new Visit(id, patientId, arrivedAt, emergency, charges);
    }

    /** An emergency's pending charge, or a visit given back whole charged again. */
    public Visit charge(Amount amount, Means means, String by, LocalDateTime at) {
        if (current().isPresent()) {
            throw new ChargeRuleViolationException(Reason.ALREADY_CHARGED, "The visit was charged already");
        }
        List<Charge> after = new ArrayList<>(charges);
        after.add(Charge.of(amount, means, by, at));
        return new Visit(id, patientId, arrivedAt, emergency, after);
    }

    /** Some or all of what is left of the current charge, given back. */
    public Visit refund(Amount amount, String reason, String by, LocalDateTime at) {
        Charge charge = current().orElseThrow(() -> new ChargeRuleViolationException(Reason.NOTHING_TO_REFUND,
                "There is nothing left to give back"));
        List<Charge> after = new ArrayList<>(charges);
        after.set(after.size() - 1, charge.refund(amount, reason, by, at));
        return new Visit(id, patientId, arrivedAt, emergency, after);
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

    /** Every charge, in the order they were recorded. */
    public List<Charge> charges() {
        return charges;
    }

    /** The charge with something left of it, if any: the last one. */
    public Optional<Charge> current() {
        return charges.isEmpty() || charges.getLast().settled() ? Optional.empty() : Optional.of(charges.getLast());
    }

    /** Never charged yet: an emergency's, only. */
    public boolean pending() {
        return charges.isEmpty();
    }
}
