package com.clinic.schedule.cashdesk.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

/**
 * Each time reception puts a patient into a queue, and what was charged for it (D-87). Its id is the
 * queue's entry, so giving the patient to another practitioner, or calling them again, is the same visit.
 *
 * <p>A visit that is not an emergency is charged as it starts, or it does not start. An emergency may be
 * charged then, or is left pending and charged later, or never. Immutable: charging answers a new visit
 * (D-17).
 */
public final class Visit {

    private final UUID id;
    private final UUID patientId;
    private final LocalDateTime arrivedAt;
    private final boolean emergency;
    private final Charge charge;

    private Visit(UUID id, UUID patientId, LocalDateTime arrivedAt, boolean emergency, Charge charge) {
        if (id == null || patientId == null || arrivedAt == null) {
            throw new IllegalArgumentException("A visit needs its id, its patient and when it started");
        }
        if (!emergency && charge == null) {
            throw new ChargeRuleViolationException(Reason.CHARGE_REQUIRED,
                    "A visit that is not an emergency is charged when the patient comes in");
        }
        this.id = id;
        this.patientId = patientId;
        this.arrivedAt = arrivedAt;
        this.emergency = emergency;
        this.charge = charge;
    }

    /**
     * The patient came into a queue.
     *
     * @param amount what reception charged; empty only for an emergency, whose charge is left pending
     * @param by the username of whoever brought them in
     */
    public static Visit arrive(UUID id, UUID patientId, boolean emergency, Optional<Amount> amount, String by,
            LocalDateTime at) {
        return new Visit(id, patientId, at, emergency, amount.map(paid -> Charge.inCash(paid, by, at)).orElse(null));
    }

    /** A visit as it was recorded, read back. */
    public static Visit recorded(UUID id, UUID patientId, LocalDateTime arrivedAt, boolean emergency, Charge charge) {
        return new Visit(id, patientId, arrivedAt, emergency, charge);
    }

    /** An emergency's pending charge, recorded. */
    public Visit charge(Amount amount, String by, LocalDateTime at) {
        if (charge != null) {
            throw new ChargeRuleViolationException(Reason.ALREADY_CHARGED, "The visit was charged already");
        }
        return new Visit(id, patientId, arrivedAt, emergency, Charge.inCash(amount, by, at));
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

    public Optional<Charge> charge() {
        return Optional.ofNullable(charge);
    }

    /** Not charged yet: an emergency's, only. */
    public boolean pending() {
        return charge == null;
    }
}
