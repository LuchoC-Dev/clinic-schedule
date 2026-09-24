package com.clinic.schedule.consultation.adapter.in.http;

import java.util.List;
import java.util.UUID;

import com.clinic.schedule.consultation.application.usecase.RecordConsultationUseCase;
import com.clinic.schedule.consultation.domain.Consultation;
import com.clinic.schedule.consultation.domain.ConsultationId;
import com.clinic.schedule.consultation.domain.ConsultationItem;
import com.clinic.schedule.consultation.domain.ItemKind;
import com.clinic.schedule.consultation.domain.PatientId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of a consultation. {@code id} is optional and makes a retry safe (D-19). There is no
 * practitioner and no time: they are whoever sends it, and now (D-51).
 */
record RecordConsultationRequest(
        UUID id,

        @NotNull(message = "the patient is required")
        UUID patientId,

        @NotBlank(message = "the notes are required")
        @Size(max = Consultation.MAX_NOTES_LENGTH, message = "the notes are at most 10000 characters")
        String notes,

        @Size(max = Consultation.MAX_ITEMS, message = "a consultation has at most 50 items")
        List<@NotNull(message = "an item cannot be empty") @Valid Item> items) {

    record Item(
            @NotNull(message = "the kind is required")
            ItemKind kind,

            @NotBlank(message = "the description is required")
            @Size(max = ConsultationItem.MAX_DESCRIPTION_LENGTH, message = "the description is at most 2000 characters")
            String description) {
    }

    RecordConsultationUseCase.Command toCommand() {
        return new RecordConsultationUseCase.Command(
                id == null ? ConsultationId.newId() : new ConsultationId(id),
                new PatientId(patientId),
                notes,
                items == null ? List.of()
                        : items.stream().map(item -> new ConsultationItem(item.kind(), item.description())).toList());
    }
}
