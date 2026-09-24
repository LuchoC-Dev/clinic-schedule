package com.clinic.schedule.consultation.adapter.in.http;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.consultation.domain.Consultation;
import com.clinic.schedule.consultation.domain.ItemKind;

/** A consultation as the API shows it, with its practitioner's name next to the id. */
record ConsultationResponse(
        UUID id,
        UUID patientId,
        UUID practitionerId,
        String practitionerName,
        LocalDateTime recordedAt,
        String notes,
        List<Item> items) {

    record Item(ItemKind kind, String description) {
    }

    static ConsultationResponse of(Consultation consultation, String practitionerName) {
        return new ConsultationResponse(
                consultation.id().value(),
                consultation.patientId().value(),
                consultation.practitionerId().value(),
                practitionerName,
                consultation.recordedAt(),
                consultation.notes(),
                consultation.items().stream().map(item -> new Item(item.kind(), item.description())).toList());
    }
}
