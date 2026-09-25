package com.clinic.schedule.clinicalrecord.adapter.in.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.clinicalrecord.application.exception.CheckNotFoundException;
import com.clinic.schedule.clinicalrecord.application.exception.ClinicalRecordAccessDeniedException;
import com.clinic.schedule.clinicalrecord.application.exception.ConsultationChangedException;
import com.clinic.schedule.clinicalrecord.domain.exception.CheckNotPendingException;
import com.clinic.schedule.clinicalrecord.domain.exception.ReasonRequiredException;
import com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * Turns this module's refusals into HTTP answers, for its own controller only (D-23). Malformed
 * requests are answered once for every module, in {@code shared/http}.
 */
@RestControllerAdvice(assignableTypes = {ConsultationController.class, CheckController.class})
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

    /** A check has one result, and it never changes: a second one is refused (D-53). */
    @ExceptionHandler(ResultAlreadyRecordedException.class)
    ResponseEntity<ErrorResponse> resultAlreadyRecorded(ResultAlreadyRecordedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e.getMessage()));
    }

    /** Access asked to report a check that does not exist (D-54). */
    @ExceptionHandler(CheckNotFoundException.class)
    ResponseEntity<ErrorResponse> checkNotFound(CheckNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, "Not found", e.getMessage()));
    }

    /** Access asked to report a check that already has its result (D-54). */
    @ExceptionHandler(CheckNotPendingException.class)
    ResponseEntity<ErrorResponse> checkNotPending(CheckNotPendingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e.getMessage()));
    }

    /** Access asked without a reason, when the practice requires one (D-54). */
    @ExceptionHandler(ReasonRequiredException.class)
    ResponseEntity<ErrorResponse> reasonRequired(ReasonRequiredException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }

    /** A consultation that breaks its own rules, when Bean Validation did not already catch it. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }
}
