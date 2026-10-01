package com.clinic.schedule.appointment.adapter.in.http;

import java.util.UUID;

import com.clinic.schedule.appointment.application.usecase.CancelAppointmentUseCase;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.CancellationRequester;

import jakarta.validation.constraints.NotNull;

/**
 * The body of a cancellation: who asked for it, and reception's optional note (D-76). The
 * appointment comes from the path; who cancelled it and when, from the session and the clock.
 */
record CancelAppointmentRequest(
        @NotNull(message = "who asked for the cancellation is required")
        CancellationRequester requestedBy,
        String note) {

    CancelAppointmentUseCase.Command toCommand(UUID id) {
        return new CancelAppointmentUseCase.Command(new AppointmentId(id), requestedBy, note);
    }
}
