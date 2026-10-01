package com.clinic.schedule.appointment.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.appointment.application.usecase.ListCancellationsUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.Cancellation;
import com.clinic.schedule.appointment.domain.CancellationOrigin;
import com.clinic.schedule.appointment.domain.CancellationRequester;

/**
 * The cancellations as the admins get them, to follow what the staff does (D-76): who cancelled, when,
 * how and who asked, and the practitioner and time of the appointment. Never the patient, whom the
 * admins do not read, nor reception's note, which may say something close to clinical: this type has
 * no place for either.
 */
record CancellationLogResponse(LocalDate from, LocalDate to, boolean limitReached, List<Entry> cancellations) {

    record Entry(LocalDateTime cancelledAt, String cancelledBy, CancellationOrigin origin,
            CancellationRequester requestedBy, Practitioner practitioner, LocalDateTime startsAt) {
    }

    record Practitioner(UUID id, String fullName) {
    }

    static CancellationLogResponse of(ListCancellationsUseCase.Result result, Map<UUID, String> practitionerNames) {
        return new CancellationLogResponse(result.from(), result.to(), result.limitReached(), result.cancelled().stream()
                .map(appointment -> entry(appointment, practitionerNames))
                .toList());
    }

    private static Entry entry(Appointment appointment, Map<UUID, String> practitionerNames) {
        Cancellation cancellation = appointment.cancellation().orElseThrow();
        UUID practitionerId = appointment.practitionerId().value();
        return new Entry(cancellation.at(), cancellation.by(), cancellation.origin(), cancellation.requestedBy(),
                new Practitioner(practitionerId, practitionerNames.get(practitionerId)),
                appointment.slot().startsAt());
    }
}
