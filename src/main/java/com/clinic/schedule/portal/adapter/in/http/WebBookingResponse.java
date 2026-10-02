package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.application.usecase.BookOnWebUseCase;

/** An appointment booked on the web, and whether its confirmation went out by email (D-82). */
record WebBookingResponse(UUID id, LocalDateTime startsAt, String practitioner, boolean confirmationSent) {

    static WebBookingResponse of(BookOnWebUseCase.Result result) {
        return new WebBookingResponse(result.appointmentId(), result.startsAt(), result.practitioner(),
                result.confirmationSent());
    }
}
