package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.application.usecase.BookOnWebUseCase;

/** An appointment booked on the web, for whom, and whether its confirmation went out by email (D-82, D-85). */
record WebBookingResponse(UUID id, UUID patientId, LocalDateTime startsAt, String practitioner,
        boolean confirmationSent) {

    static WebBookingResponse of(BookOnWebUseCase.Result result) {
        return new WebBookingResponse(result.appointmentId(), result.patientId(), result.startsAt(),
                result.practitioner(),
                result.confirmationSent());
    }
}
