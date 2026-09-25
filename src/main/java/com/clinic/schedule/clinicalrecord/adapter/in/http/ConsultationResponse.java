package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.Consultation;
import com.clinic.schedule.clinicalrecord.domain.ItemKind;

/**
 * A consultation as the API shows it: with the practitioners' names next to their ids, and the
 * checks done during it, so a client does not have to pair them (D-53).
 */
record ConsultationResponse(
        UUID id,
        UUID patientId,
        UUID practitionerId,
        String practitionerName,
        LocalDateTime recordedAt,
        String notes,
        List<Item> items,
        List<CheckResponse> checks,
        List<AddendumResponse> addenda) {

    record Item(ItemKind kind, String description) {
    }

    record AddendumResponse(UUID id, UUID practitionerId, String practitionerName, LocalDateTime writtenAt,
            String text) {
    }

    /**
     * @param checks the checks done during this consultation
     * @param names every practitioner's name by id; an unknown one is left null
     */
    static ConsultationResponse of(Consultation consultation, List<Check> checks, Map<UUID, String> names) {
        return new ConsultationResponse(
                consultation.id().value(),
                consultation.patientId().value(),
                consultation.practitionerId().value(),
                names.get(consultation.practitionerId().value()),
                consultation.recordedAt(),
                consultation.notes(),
                consultation.items().stream().map(item -> new Item(item.kind(), item.description())).toList(),
                checks.stream().map(check -> CheckResponse.of(check, names)).toList(),
                consultation.addenda().stream()
                        .map(addendum -> new AddendumResponse(addendum.id().value(), addendum.author().value(),
                                names.get(addendum.author().value()), addendum.writtenAt(), addendum.text()))
                        .toList());
    }
}
