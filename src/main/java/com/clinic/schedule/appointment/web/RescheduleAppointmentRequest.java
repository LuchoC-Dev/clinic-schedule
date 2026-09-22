package com.clinic.schedule.appointment.web;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.appointment.application.RescheduleAppointmentUseCase;
import com.clinic.schedule.appointment.domain.AppointmentId;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The body of a reschedule request. The appointment comes from the path. */
record RescheduleAppointmentRequest(
        @NotNull(message = "the start time is required")
        LocalDateTime startsAt,

        @Min(value = 1, message = "the duration must be at least one minute")
        int durationMinutes) {

    RescheduleAppointmentUseCase.Command toCommand(UUID id) {
        return new RescheduleAppointmentUseCase.Command(new AppointmentId(id), startsAt, durationMinutes);
    }
}
