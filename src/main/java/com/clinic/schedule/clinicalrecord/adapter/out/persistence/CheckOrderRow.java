package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.OrderOutcome;
import com.clinic.schedule.clinicalrecord.domain.OrderStatus;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * An order as a row of {@code check_order} — {@code order} is a reserved word in SQL. Separate from
 * the aggregate (D-16).
 *
 * <p>The version guards its status: two checks fulfilling it at the same time, or a check and its
 * cancellation, both read it open, and the second write is refused (D-55).
 */
@Entity
@Table(name = "check_order")
class CheckOrderRow {

    @Id
    private UUID id;

    @Version
    private long version;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "ordered_by", nullable = false)
    private UUID orderedBy;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "consultation_id", nullable = false)
    private UUID consultationId;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "fulfilled_by")
    private UUID fulfilledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    protected CheckOrderRow() {
        // required by JPA
    }

    static CheckOrderRow from(CheckOrder order) {
        CheckOrderRow row = new CheckOrderRow();
        row.id = order.id().value();
        row.patientId = order.patientId().value();
        row.orderedBy = order.orderedBy().value();
        row.orderedAt = order.orderedAt();
        row.consultationId = order.consultationId().value();
        row.description = order.description();
        row.statusFrom(order);
        return row;
    }

    /**
     * Writes the order's outcome onto this row, as read in this transaction: nothing else changes.
     * The status column repeats what the outcome says, so the table can be read on its own.
     */
    void statusFrom(CheckOrder order) {
        status = order.status();
        OrderOutcome outcome = order.outcome().orElse(null);
        fulfilledBy = outcome instanceof OrderOutcome.Fulfilled fulfilled ? fulfilled.check().value() : null;
        cancelledAt = outcome instanceof OrderOutcome.Cancelled cancelled ? cancelled.at() : null;
        cancellationReason = outcome instanceof OrderOutcome.Cancelled cancelled ? cancelled.reason() : null;
    }

    CheckOrder toOrder() {
        OrderOutcome outcome = switch (status) {
            case OPEN -> null;
            case FULFILLED -> new OrderOutcome.Fulfilled(new CheckId(fulfilledBy));
            case CANCELLED -> new OrderOutcome.Cancelled(cancelledAt, cancellationReason);
        };
        return CheckOrder.restore(new CheckOrderId(id), new PatientId(patientId), new PractitionerId(orderedBy),
                orderedAt, new ConsultationId(consultationId), description, outcome);
    }
}
