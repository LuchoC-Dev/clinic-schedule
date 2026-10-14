package com.clinic.schedule.patient.presentation.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.patient.entity.IncompleteDocumentException;
import com.clinic.schedule.patient.entity.UnverifiableIdentityException;
import com.clinic.schedule.patient.entity.VerificationRequiredException;
import com.clinic.schedule.patient.service.DuplicateDocumentException;
import com.clinic.schedule.patient.service.PatientNotFoundException;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * This module's exceptions as HTTP answers, scoped to its controller so no shared class has
 * to import them (D-23).
 *
 * <p>Validation errors are handled once for every module, in {@code shared/http}.
 */
@RestControllerAdvice(assignableTypes = PatientController.class)
class PatientExceptionHandler {

    @ExceptionHandler(PatientNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(PatientNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not found", e));
    }

    /** A taken document, or a patient with a web account left unverified (D-79): refused for the state. */
    @ExceptionHandler({DuplicateDocumentException.class, VerificationRequiredException.class})
    ResponseEntity<ErrorResponse> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e));
    }

    /**
     * Half a document, a number that does not fit its type, or a document said to be seen that is
     * not there: the request is malformed.
     */
    @ExceptionHandler({IncompleteDocumentException.class, UnverifiableIdentityException.class,
            IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> malformedRequest(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad request", e));
    }
}
