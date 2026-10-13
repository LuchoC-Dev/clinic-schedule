package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.CopyDelivery;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * A copy of a record as the practitioners see it (D-95): whose, who asked and when, how it is delivered, its code,
 * when the record is copied as it stood and how many times it was put together, by when it is due and the minutes
 * left — negative once late —, who authenticated it and when, and whether the practitioner asking may read the
 * record. Nothing of the record itself: that is its PDF.
 */
record RecordCopyResponse(
        UUID id,
        Person patient,
        Person requestedBy,
        LocalDateTime requestedAt,
        CopyDelivery delivery,
        String code,
        LocalDateTime assembledAt,
        int assembly,
        LocalDateTime deadline,
        long minutesLeft,
        boolean late,
        Authentication authentication,
        LocalDateTime handedOverAt,
        boolean mayRead) {

    /** {@code document} is null when the patient gave none. */
    record Person(UUID id, String fullName, String document) {
    }

    record Authentication(UUID practitionerId, String practitionerName, String username, LocalDateTime at) {
    }

    static RecordCopyResponse of(RecordCopy copy, boolean mayRead, LocalDateTime now, Map<UUID, PatientSummary> people,
            Map<UUID, String> names) {
        return new RecordCopyResponse(copy.id().value(),
                person(copy.patientId().value(), people),
                person(copy.requestedBy(), people),
                copy.requestedAt(),
                copy.delivery(),
                copy.code().formatted(),
                copy.assembledAt(),
                copy.assembly(),
                copy.deadline(),
                copy.timeLeft(now).toMinutes(),
                copy.isLate(now),
                copy.authentication()
                        .map(authentication -> new Authentication(authentication.practitionerId().value(),
                                names.get(authentication.practitionerId().value()), authentication.username(),
                                authentication.at()))
                        .orElse(null),
                copy.handover().map(RecordCopy.Handover::at).orElse(null),
                mayRead);
    }

    static Person person(UUID id, Map<UUID, PatientSummary> people) {
        PatientSummary person = people.get(id);
        return new Person(id, person == null ? null : person.fullName(), person == null ? null : person.document());
    }
}
