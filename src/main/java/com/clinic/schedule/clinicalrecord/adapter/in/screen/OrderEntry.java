package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.OrderOutcome;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

/**
 * One order of a clinical record screen: what it asks for, where it stands in words, and whether
 * the reader may cancel it — only its author, while it is open (D-55).
 */
record OrderEntry(UUID id, String description, boolean open, String state, boolean mayCancel) {

    /**
     * @param checks every check of the record by id, to say which one fulfilled the order
     * @param reader the practitioner reading the record
     */
    static OrderEntry of(CheckOrder order, Map<CheckId, Check> checks, PractitionerId reader) {
        return new OrderEntry(order.id().value(), order.description(), order.isOpen(), stateOf(order, checks),
                order.isOpen() && order.orderedBy().equals(reader));
    }

    private static String stateOf(CheckOrder order, Map<CheckId, Check> checks) {
        return order.outcome()
                .map(outcome -> switch (outcome) {
                    case OrderOutcome.Fulfilled fulfilled -> checks.containsKey(fulfilled.check())
                            ? "Cumplida el " + ClinicalRecordEntry.format(checks.get(fulfilled.check()).performedAt())
                            : "Cumplida";
                    case OrderOutcome.Cancelled cancelled -> "Anulada: " + cancelled.reason();
                })
                .orElse("Abierta");
    }
}
