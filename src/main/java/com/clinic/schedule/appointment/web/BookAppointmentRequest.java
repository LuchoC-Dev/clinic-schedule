package com.clinic.schedule.appointment.web;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.appointment.application.BookAppointmentUseCase;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of a booking request.
 *
 * <p>{@code id} is optional. A client that wants a retry to be safe sends one it generated
 * and reuses it; a client that does not care leaves it out and one is generated here (D-19).
 *
 * <p>Validation here is about the shape of the request — something is missing, a number is
 * out of range. Whether the slot is free is a business rule and belongs to the domain, which
 * is why nothing about overlap appears in these annotations.
 */
record BookAppointmentRequest(
        UUID id,

        @NotNull(message = "the patient is required")
        UUID patientId,

        @NotNull(message = "the practitioner is required")
        UUID practitionerId,

        @NotNull(message = "the start time is required")
        LocalDateTime startsAt,

        @Min(value = 1, message = "the duration must be at least one minute")
        int durationMinutes,

        @NotBlank(message = "the reason is required")
        @Size(max = 100, message = "the reason is at most 100 characters")
        String reason,

        @Size(max = 500, message = "the note is at most 500 characters")
        String note) {

    BookAppointmentUseCase.Command toCommand() {
        return new BookAppointmentUseCase.Command(
                id == null ? AppointmentId.newId() : new AppointmentId(id),
                new PatientId(patientId),
                new PractitionerId(practitionerId),
                startsAt,
                durationMinutes,
                reason,
                note);
    }
}
