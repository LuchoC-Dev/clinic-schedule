package com.clinic.schedule.appointment.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.appointment.application.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.UnknownReasonException;
import com.clinic.schedule.appointment.domain.BookingRuleViolationException;
import com.clinic.schedule.shared.web.ErrorResponse;

/**
 * Turns this module's exceptions into HTTP answers. Scoped to the appointment controller
 * rather than global, so the module keeps its own translation and no shared class has to
 * import its exceptions (D-05).
 *
 * <p>A booking rule that says no is a 409: the request was well formed and the client can
 * retry with another time. Validation errors are handled once for every module, in
 * {@code shared/web}.
 */
@RestControllerAdvice(assignableTypes = AppointmentController.class)
class AppointmentExceptionHandler {

    @ExceptionHandler(AppointmentNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(AppointmentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not found", e.getMessage()));
    }

    /** The request names a reason this practitioner does not have. */
    @ExceptionHandler(UnknownReasonException.class)
    ResponseEntity<ErrorResponse> unknownReason(UnknownReasonException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }

    /** Overlaps and working hours alike: the request was fine, the slot is not available. */
    @ExceptionHandler(BookingRuleViolationException.class)
    ResponseEntity<ErrorResponse> bookingRule(BookingRuleViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e.getMessage()));
    }
}
