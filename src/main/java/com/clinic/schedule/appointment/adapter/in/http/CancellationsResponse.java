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
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * The cancellations as reception gets them (D-76): the appointment — its patient, practitioner and
 * time — and all that was recorded of its cancellation, reception's note included.
 */
record CancellationsResponse(LocalDate from, LocalDate to, boolean limitReached, List<Entry> cancellations) {

    /** {@code note} is null when reception wrote none. */
    record Entry(UUID appointmentId, LocalDateTime startsAt, Practitioner practitioner, Patient patient,
            LocalDateTime cancelledAt, String cancelledBy, CancellationOrigin origin, CancellationRequester requestedBy,
            String note) {
    }

    record Practitioner(UUID id, String fullName) {
    }

    /** {@code document} is null when the patient gave none. */
    record Patient(UUID id, String fullName, String document) {
    }

    static CancellationsResponse of(ListCancellationsUseCase.Result result, Map<UUID, PatientSummary> patients,
            Map<UUID, String> practitionerNames) {
        return new CancellationsResponse(result.from(), result.to(), result.limitReached(), result.cancelled().stream()
                .map(appointment -> entry(appointment, patients, practitionerNames))
                .toList());
    }

    private static Entry entry(Appointment appointment, Map<UUID, PatientSummary> patients,
            Map<UUID, String> practitionerNames) {
        Cancellation cancellation = appointment.cancellation().orElseThrow();
        UUID practitionerId = appointment.practitionerId().value();
        UUID patientId = appointment.patientId().value();
        PatientSummary patient = patients.get(patientId);
        return new Entry(appointment.id().value(), appointment.slot().startsAt(),
                new Practitioner(practitionerId, practitionerNames.get(practitionerId)),
                new Patient(patientId, patient == null ? null : patient.fullName(),
                        patient == null ? null : patient.document()),
                cancellation.at(), cancellation.by(), cancellation.origin(), cancellation.requestedBy(),
                cancellation.note());
    }
}
