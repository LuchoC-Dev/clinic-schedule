package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * A copy on paper as reception sees it (D-95): whose, who asked for it — whoever comes for it shows their
 * document —, when it was authenticated, and when it was handed over. Never who authenticated it nor anything it
 * holds: the envelope is sealed (D-51).
 */
record EnvelopeResponse(UUID id, RecordCopyResponse.Person patient, RecordCopyResponse.Person requestedBy,
        LocalDateTime authenticatedAt, LocalDateTime handedOverAt) {

    static EnvelopeResponse of(RecordCopy copy, Map<UUID, PatientSummary> people) {
        return new EnvelopeResponse(copy.id().value(),
                RecordCopyResponse.person(copy.patientId().value(), people),
                RecordCopyResponse.person(copy.requestedBy(), people),
                copy.authentication().map(RecordCopy.Authentication::at).orElse(null),
                copy.handover().map(RecordCopy.Handover::at).orElse(null));
    }
}
