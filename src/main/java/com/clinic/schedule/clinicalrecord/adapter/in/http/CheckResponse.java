package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;

/**
 * A check as the API shows it, with the practitioners' names next to their ids. {@code result} is
 * null while it is pending; {@code consultationId} is null for a check done without one.
 */
record CheckResponse(
        UUID id,
        UUID patientId,
        UUID performedBy,
        String performedByName,
        LocalDateTime performedAt,
        String description,
        UUID consultationId,
        boolean pending,
        Result result) {

    record Result(UUID authorId, String authorName, LocalDateTime writtenAt, String text) {
    }

    /** @param names every practitioner's name by id; an unknown one is left null */
    static CheckResponse of(Check check, Map<UUID, String> names) {
        return new CheckResponse(
                check.id().value(),
                check.patientId().value(),
                check.performedBy().value(),
                names.get(check.performedBy().value()),
                check.performedAt(),
                check.description(),
                check.consultationId().map(ConsultationId::value).orElse(null),
                check.isPending(),
                check.result()
                        .map(result -> new Result(result.author().value(), names.get(result.author().value()),
                                result.writtenAt(), result.text()))
                        .orElse(null));
    }
}
