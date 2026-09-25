package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.OrderOutcome;
import com.clinic.schedule.clinicalrecord.domain.OrderStatus;

/**
 * An order for a check as the API shows it: what it asks for, who issued it, and where it stands —
 * with the check that fulfilled it, or when and why it was cancelled (D-55).
 */
record CheckOrderResponse(
        UUID id,
        UUID patientId,
        UUID orderedBy,
        String orderedByName,
        LocalDateTime orderedAt,
        UUID consultationId,
        String description,
        OrderStatus status,
        UUID fulfilledBy,
        LocalDateTime cancelledAt,
        String cancellationReason) {

    /** @param names every practitioner's name by id; an unknown one is left null */
    static CheckOrderResponse of(CheckOrder order, Map<UUID, String> names) {
        OrderOutcome outcome = order.outcome().orElse(null);
        return new CheckOrderResponse(
                order.id().value(),
                order.patientId().value(),
                order.orderedBy().value(),
                names.get(order.orderedBy().value()),
                order.orderedAt(),
                order.consultationId().value(),
                order.description(),
                order.status(),
                outcome instanceof OrderOutcome.Fulfilled fulfilled ? fulfilled.check().value() : null,
                outcome instanceof OrderOutcome.Cancelled cancelled ? cancelled.at() : null,
                outcome instanceof OrderOutcome.Cancelled cancelled ? cancelled.reason() : null);
    }
}
