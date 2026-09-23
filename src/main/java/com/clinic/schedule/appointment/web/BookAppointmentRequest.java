package com.clinic.schedule.appointment.web;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.appointment.application.BookAppointmentUseCase;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of a booking request.
 *
 * <p>{@code id} is optional. A client that wants a retry to be safe sends one it generated
 * and reuses it; a client that does not care leaves it out and one is generated here (D-19).
 *
 * <p>There is no duration: it follows from the reason, as configured for the practitioner
 * (D-08).
 *
 * <p>Validation here is about the shape of the request. Whether the reason exists, whether
 * the practitioner works then and whether the slot is free are business rules, answered by
 * the use case.
 */
record BookAppointmentRequest(
        UUID id,

        @NotNull(message = "the patient is required")
        UUID patientId,

        @NotNull(message = "the practitioner is required")
        UUID practitionerId,

        @NotNull(message = "the reason is required")
        UUID reasonId,

        @NotNull(message = "the start time is required")
        LocalDateTime startsAt,

        @Size(max = 500, message = "the note is at most 500 characters")
        String note) {

    BookAppointmentUseCase.Command toCommand() {
        return new BookAppointmentUseCase.Command(
                id == null ? AppointmentId.newId() : new AppointmentId(id),
                new PatientId(patientId),
                new PractitionerId(practitionerId),
                reasonId,
                startsAt,
                note);
    }
}
