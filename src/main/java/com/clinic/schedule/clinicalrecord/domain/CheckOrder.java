package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;
import java.util.Optional;

import com.clinic.schedule.clinicalrecord.domain.exception.NotTheOrderAuthorException;
import com.clinic.schedule.clinicalrecord.domain.exception.OrderNotOpenException;

/**
 * An order for a check: a practitioner who needs a check they cannot do authorizes it (D-55).
 * Whoever fulfils it does not matter — here, or at another practice.
 *
 * <p>It is issued during a consultation, for now. It is open until it has an {@link OrderOutcome}:
 * one check fulfils it — one order, one check — or its author cancels it, with a reason. An order
 * fulfilled elsewhere stays open: that is none of this application's business yet.
 *
 * <p>The order says which check fulfilled it; the check does not know about the order, so a check
 * done with no order, or elsewhere, looks the same.
 *
 * <p>Plain domain object and immutable: its operations return a new order.
 */
public class CheckOrder {

    /** As long as the column that stores it, the same as a check (D-38). */
    public static final int MAX_DESCRIPTION_LENGTH = 2_000;

    private final CheckOrderId id;
    private final PatientId patientId;
    private final PractitionerId orderedBy;
    private final LocalDateTime orderedAt;
    private final ConsultationId consultationId;
    private final String description;
    private final OrderOutcome outcome;

    private CheckOrder(CheckOrderId id, PatientId patientId, PractitionerId orderedBy, LocalDateTime orderedAt,
            ConsultationId consultationId, String description, OrderOutcome outcome) {
        this.id = id;
        this.patientId = patientId;
        this.orderedBy = orderedBy;
        this.orderedAt = orderedAt;
        this.consultationId = consultationId;
        this.description = description;
        this.outcome = outcome;
    }

    /** A new order, open, issued during a consultation. */
    public static CheckOrder issue(CheckOrderId id, PatientId patientId, PractitionerId orderedBy,
            LocalDateTime orderedAt, ConsultationId consultationId, String description) {
        return restore(id, patientId, orderedBy, orderedAt, consultationId, description, null);
    }

    /**
     * An order read back from storage, checked the same way, so there is no way to obtain an
     * invalid one. Public because the storage adapter lives in another package.
     *
     * @param outcome null while it is open
     */
    public static CheckOrder restore(CheckOrderId id, PatientId patientId, PractitionerId orderedBy,
            LocalDateTime orderedAt, ConsultationId consultationId, String description, OrderOutcome outcome) {
        if (id == null || patientId == null || orderedBy == null || orderedAt == null || consultationId == null) {
            throw new IllegalArgumentException("An order needs an id, a patient, who issued it, when and its consultation");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("An order needs a description of the check it asks for");
        }
        String stripped = description.strip();
        if (stripped.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("An order is described in at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        if (outcome instanceof OrderOutcome.Cancelled cancelled && cancelled.at().isBefore(orderedAt)) {
            throw new IllegalArgumentException("An order is cancelled after it was issued");
        }
        return new CheckOrder(id, patientId, orderedBy, orderedAt, consultationId, stripped, outcome);
    }

    /**
     * The same order, fulfilled by that check: done to the same patient, after the order.
     *
     * @throws OrderNotOpenException when it was already fulfilled or cancelled
     */
    public CheckOrder fulfilledBy(Check check) {
        requireOpen();
        if (check == null || !check.patientId().equals(patientId)) {
            throw new IllegalArgumentException("An order is fulfilled by a check of its own patient");
        }
        if (check.performedAt().isBefore(orderedAt)) {
            throw new IllegalArgumentException("An order is fulfilled by a check done after it was issued");
        }
        return withOutcome(new OrderOutcome.Fulfilled(check.id()));
    }

    /**
     * The same order, cancelled by its author, with a reason.
     *
     * @throws NotTheOrderAuthorException when someone else asks
     * @throws OrderNotOpenException when it was already fulfilled or cancelled
     */
    public CheckOrder cancelled(PractitionerId by, LocalDateTime at, String reason) {
        if (by == null || !by.equals(orderedBy)) {
            throw new NotTheOrderAuthorException(id);
        }
        requireOpen();
        return restore(id, patientId, orderedBy, orderedAt, consultationId, description,
                new OrderOutcome.Cancelled(at, reason));
    }

    private CheckOrder withOutcome(OrderOutcome newOutcome) {
        return new CheckOrder(id, patientId, orderedBy, orderedAt, consultationId, description, newOutcome);
    }

    private void requireOpen() {
        if (outcome != null) {
            throw new OrderNotOpenException(id);
        }
    }

    public boolean isOpen() {
        return outcome == null;
    }

    /** Where it stands: open while it has no outcome, otherwise what its outcome says. */
    public OrderStatus status() {
        return outcome == null ? OrderStatus.OPEN : outcome.status();
    }

    /** How it ended — fulfilled or cancelled — or empty while it is open. */
    public Optional<OrderOutcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    public CheckOrderId id() {
        return id;
    }

    public PatientId patientId() {
        return patientId;
    }

    public PractitionerId orderedBy() {
        return orderedBy;
    }

    public LocalDateTime orderedAt() {
        return orderedAt;
    }

    public ConsultationId consultationId() {
        return consultationId;
    }

    public String description() {
        return description;
    }
}
