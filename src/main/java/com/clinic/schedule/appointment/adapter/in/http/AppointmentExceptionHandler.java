package com.clinic.schedule.appointment.adapter.in.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.appointment.application.exception.AgendaAccessDeniedException;
import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.exception.UnknownPatientException;
import com.clinic.schedule.appointment.application.exception.UnknownPractitionerException;
import com.clinic.schedule.appointment.application.exception.UnknownReasonException;
import com.clinic.schedule.appointment.domain.exception.BookingRuleViolationException;
import com.clinic.schedule.patient.api.PatientRegistrationRefusedException;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * Turns this module's exceptions into HTTP answers. Scoped to this module's controllers
 * rather than global, so the module keeps its own translation and no shared class has to
 * import its exceptions (D-05).
 *
 * <p>A booking rule that says no is a 409: the request was well formed and the client can
 * retry with another time. Validation errors are handled once for every module, in
 * {@code shared/http}.
 */
@RestControllerAdvice(assignableTypes = {AppointmentController.class, AgendaController.class,
        TimeOffController.class, CancellationController.class})
class AppointmentExceptionHandler {

    @ExceptionHandler(AppointmentNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(AppointmentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not found", e));
    }

    /**
     * The request names someone or something that cannot be used: a patient who is not
     * registered, a practitioner who does not exist, a reason the practitioner lacks. Or it
     * describes a span that makes no sense, such as time off ending before it starts.
     */
    @ExceptionHandler({UnknownPatientException.class, UnknownPractitionerException.class,
            UnknownReasonException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad request", e));
    }

    /** A practitioner asking for someone else's agenda: they are known, but not allowed that (D-39). */
    @ExceptionHandler(AgendaAccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(AgendaAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Forbidden", e));
    }

    /**
     * A new patient booked at once the patient module would not register (D-75): a document someone else has is a
     * conflict, anything else malformed. Its reason is its code (D-100).
     */
    @ExceptionHandler(PatientRegistrationRefusedException.class)
    ResponseEntity<ErrorResponse> patientRefused(PatientRegistrationRefusedException e) {
        return e.reason() == PatientRegistrationRefusedException.Reason.DUPLICATE_DOCUMENT
                ? ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e))
                : ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e));
    }

    /** Overlaps, working hours and time off alike: the request was fine, the slot is not available. */
    @ExceptionHandler(BookingRuleViolationException.class)
    ResponseEntity<ErrorResponse> bookingRule(BookingRuleViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e));
    }
}
