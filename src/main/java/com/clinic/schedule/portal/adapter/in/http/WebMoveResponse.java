package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.application.usecase.MoveOwnAppointmentUseCase;

/** An appointment moved on the web, for whom, its new time, and whether its confirmation went out (D-93). */
record WebMoveResponse(UUID id, UUID patientId, LocalDateTime startsAt, String practitioner,
        boolean confirmationSent) {

    static WebMoveResponse of(MoveOwnAppointmentUseCase.Result result) {
        return new WebMoveResponse(result.appointmentId(), result.patientId(), result.startsAt(),
                result.practitioner(), result.confirmationSent());
    }
}
