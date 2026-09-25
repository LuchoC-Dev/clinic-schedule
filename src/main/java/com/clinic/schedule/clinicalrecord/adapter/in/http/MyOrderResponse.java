package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.ListMyOrdersUseCase;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.CheckResult;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * An order the user issued, as their follow-up list shows it: which patient, what was asked, where
 * it stands, and who did and reported the check and when — not what the result says, which is in
 * the record (D-56).
 */
record MyOrderResponse(
        UUID orderId,
        UUID patientId,
        String patientName,
        String patientDocument,
        String description,
        LocalDateTime orderedAt,
        ListMyOrdersUseCase.Situation situation,
        LocalDateTime performedAt,
        String performedByName,
        LocalDateTime reportedAt,
        String reportedByName,
        boolean mayRead) {

    static MyOrderResponse of(ListMyOrdersUseCase.MyOrder mine, Map<UUID, PatientSummary> patients,
            Map<UUID, String> names) {
        CheckOrder order = mine.order();
        PatientSummary patient = patients.get(order.patientId().value());
        CheckResult result = mine.check().flatMap(check -> check.result()).orElse(null);
        return new MyOrderResponse(
                order.id().value(),
                order.patientId().value(),
                patient == null ? null : patient.fullName(),
                patient == null ? null : patient.document(),
                order.description(),
                order.orderedAt(),
                mine.situation(),
                mine.check().map(check -> check.performedAt()).orElse(null),
                mine.check().map(check -> names.get(check.performedBy().value())).orElse(null),
                result == null ? null : result.writtenAt(),
                result == null ? null : names.get(result.author().value()),
                mine.mayRead());
    }
}
