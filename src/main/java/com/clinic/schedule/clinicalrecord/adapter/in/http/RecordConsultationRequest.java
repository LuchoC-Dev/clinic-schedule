package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.util.List;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.application.usecase.RecordConsultationUseCase;
import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckOrder;
import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;
import com.clinic.schedule.clinicalrecord.domain.CheckResult;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.ConsultationItem;
import com.clinic.schedule.clinicalrecord.domain.ItemKind;
import com.clinic.schedule.clinicalrecord.domain.PatientId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of a consultation, with the checks done during it. {@code id} is optional and makes a
 * retry safe (D-19). There is no practitioner and no time: they are whoever sends it, and now
 * (D-51).
 */
record RecordConsultationRequest(
        UUID id,

        @NotNull(message = "the patient is required")
        UUID patientId,

        @NotBlank(message = "the notes are required")
        @Size(max = Consultation.MAX_NOTES_LENGTH, message = "the notes are at most 10000 characters")
        String notes,

        @Size(max = Consultation.MAX_ITEMS, message = "a consultation has at most 50 items")
        List<@NotNull(message = "an item cannot be empty") @Valid Item> items,

        @Size(max = Consultation.MAX_ITEMS, message = "a consultation has at most 50 checks")
        List<@NotNull(message = "a check cannot be empty") @Valid CheckDone> checks,

        @Size(max = Consultation.MAX_ITEMS, message = "a consultation has at most 50 orders")
        List<@NotNull(message = "an order cannot be empty") @Valid OrderIssued> orders) {

    record Item(
            @NotNull(message = "the kind is required")
            ItemKind kind,

            @NotBlank(message = "the description is required")
            @Size(max = ConsultationItem.MAX_DESCRIPTION_LENGTH, message = "the description is at most 2000 characters")
            String description) {
    }

    /**
     * A check done during the consultation; without a result, the result is pending (D-53). It may
     * fulfil one of the patient's open orders, and then its description may be left out (D-55).
     */
    record CheckDone(
            @Size(max = Check.MAX_DESCRIPTION_LENGTH, message = "the description is at most 2000 characters")
            String description,

            @Size(max = CheckResult.MAX_TEXT_LENGTH, message = "the result is at most 10000 characters")
            String result,

            UUID orderId) {
    }

    /** An order issued during the consultation: what check it asks for (D-55). */
    record OrderIssued(
            @NotBlank(message = "the description is required")
            @Size(max = CheckOrder.MAX_DESCRIPTION_LENGTH, message = "the description is at most 2000 characters")
            String description) {
    }

    RecordConsultationUseCase.Command toCommand() {
        return new RecordConsultationUseCase.Command(
                id == null ? ConsultationId.newId() : new ConsultationId(id),
                new PatientId(patientId),
                notes,
                items == null ? List.of()
                        : items.stream().map(item -> new ConsultationItem(item.kind(), item.description())).toList(),
                checks == null ? List.of()
                        : checks.stream()
                                .map(check -> new RecordConsultationUseCase.CheckDone(check.description(), check.result(),
                                        check.orderId() == null ? null : new CheckOrderId(check.orderId())))
                                .toList(),
                orders == null ? List.of() : orders.stream().map(OrderIssued::description).toList());
    }
}
