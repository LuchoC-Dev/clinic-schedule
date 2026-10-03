package com.clinic.schedule.portal.adapter.in.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.portal.application.exception.AccountNotFoundException;
import com.clinic.schedule.portal.application.exception.FamilyGroupNotFoundException;
import com.clinic.schedule.portal.application.exception.InvalidActivationLinkException;
import com.clinic.schedule.portal.application.exception.NotYourAppointmentException;
import com.clinic.schedule.portal.application.exception.OwnDataRefusedException;
import com.clinic.schedule.portal.application.exception.UnknownPatientException;
import com.clinic.schedule.portal.domain.exception.AccountRefusedException;
import com.clinic.schedule.portal.domain.exception.AccountStateException;
import com.clinic.schedule.portal.domain.exception.FamilyRefusedException;
import com.clinic.schedule.portal.domain.exception.WebBookingRefusedException;
import com.clinic.schedule.portal.domain.exception.WebCancellationRefusedException;
import com.clinic.schedule.shared.http.ErrorResponse;

/** Translates this module's refusals into HTTP for its own controllers only (D-23). */
@RestControllerAdvice(assignableTypes = {PatientAccountController.class, PortalController.class,
        FamilyGroupController.class})
class PortalExceptionHandler {

    /** An appointment that is not the patient's: refused, never answered as if it were not there (D-50). */
    @ExceptionHandler(NotYourAppointmentException.class)
    ResponseEntity<ErrorResponse> notYours(NotYourAppointmentException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(403, "Forbidden", e.getMessage()));
    }

    /** A link that leads to nothing is a 404; an appointment not booked any more, or too close, a 409. */
    @ExceptionHandler(WebCancellationRefusedException.class)
    ResponseEntity<ErrorResponse> cancellationRefused(WebCancellationRefusedException e) {
        if (e.reason() == WebCancellationRefusedException.Reason.GONE) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, "Not found", e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e.getMessage()));
    }

    @ExceptionHandler({AccountNotFoundException.class, FamilyGroupNotFoundException.class})
    ResponseEntity<ErrorResponse> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, "Not found", e.getMessage()));
    }

    /**
     * The patient may not have an account, the account's state does not allow what was asked, the web does
     * not book what was asked, or a family group's rules refuse it.
     */
    @ExceptionHandler({AccountRefusedException.class, AccountStateException.class, WebBookingRefusedException.class,
            FamilyRefusedException.class})
    ResponseEntity<ErrorResponse> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e.getMessage()));
    }

    /**
     * A patient who does not exist, a link that activates nothing, an email that cannot be one, a password
     * too short or too long, or a patient's own data refused: the request is malformed.
     */
    @ExceptionHandler({UnknownPatientException.class, InvalidActivationLinkException.class,
            OwnDataRefusedException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> malformedRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }
}
