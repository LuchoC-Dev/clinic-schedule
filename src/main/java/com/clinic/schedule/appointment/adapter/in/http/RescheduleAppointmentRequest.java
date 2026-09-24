package com.clinic.schedule.appointment.adapter.in.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.appointment.application.usecase.RescheduleAppointmentUseCase;
import com.clinic.schedule.appointment.domain.AppointmentId;

import jakarta.validation.constraints.NotNull;

/**
 * The body of a reschedule request: only the new start. The appointment comes from the
 * path, and it keeps the duration it was booked with.
 */
record RescheduleAppointmentRequest(
        @NotNull(message = "the start time is required")
        LocalDateTime startsAt) {

    RescheduleAppointmentUseCase.Command toCommand(UUID id) {
        return new RescheduleAppointmentUseCase.Command(new AppointmentId(id), startsAt);
    }
}
