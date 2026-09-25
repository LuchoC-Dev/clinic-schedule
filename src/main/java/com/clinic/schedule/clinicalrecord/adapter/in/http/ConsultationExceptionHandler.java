package com.clinic.schedule.clinicalrecord.adapter.in.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.ConsultationChangedException;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * Turns this module's refusals into HTTP answers, for its own controller only (D-23). Malformed
 * requests are answered once for every module, in {@code shared/http}.
 */
@RestControllerAdvice(assignableTypes = ConsultationController.class)
class ConsultationExceptionHandler {

    /** Not this user's to read or to write: a 403, never an empty record (D-50). */
    @ExceptionHandler(ClinicalRecordAccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(ClinicalRecordAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Forbidden", e.getMessage()));
    }

    /** Another addendum was added at the same time: nothing was saved, and sending it again works (D-52). */
    @ExceptionHandler(ConsultationChangedException.class)
    ResponseEntity<ErrorResponse> conflict(ConsultationChangedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e.getMessage()));
    }

    /** A consultation that breaks its own rules, when Bean Validation did not already catch it. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }
}
